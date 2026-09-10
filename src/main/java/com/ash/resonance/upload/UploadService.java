package com.ash.resonance.upload;

import com.ash.resonance.library.AudioMetadataExtractor;
import com.ash.resonance.library.MediaFile;
import com.ash.resonance.library.MediaFileRepository;
import com.ash.resonance.library.Track;
import com.ash.resonance.library.TrackRepository;
import com.ash.resonance.storage.FileStorageService;
import com.ash.resonance.upload.dto.CompleteUploadRequest;
import com.ash.resonance.upload.dto.CreateUploadRequest;
import com.ash.resonance.upload.dto.CreateUploadResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Service
public class UploadService {

	/**
	 * 4 MB: big enough to be efficient, small enough that a retry doesn't resend
	 * much.
	 */
	public static final int CHUNK_SIZE_BYTES = 4 * 1024 * 1024;

	private final UploadRepository uploadRepository;
	private final MediaFileRepository mediaFileRepository;
	private final TrackRepository trackRepository;
	private final FileStorageService storage;
	private final AudioMetadataExtractor metadataExtractor;

	public UploadService(UploadRepository uploadRepository,
			MediaFileRepository mediaFileRepository,
			TrackRepository trackRepository,
			FileStorageService storage,
			AudioMetadataExtractor metadataExtractor) {
		this.uploadRepository = uploadRepository;
		this.mediaFileRepository = mediaFileRepository;
		this.trackRepository = trackRepository;
		this.storage = storage;
		this.metadataExtractor = metadataExtractor;
	}

	@Transactional
	public CreateUploadResponse create(UUID userId, UUID deviceId, CreateUploadRequest request) {
		String hash = request.declaredHash().toLowerCase();

		// Dedup check BEFORE any bytes cross the network — the cheapest possible point.
		Optional<MediaFile> existing = mediaFileRepository.findByContentHash(hash);
		if (existing.isPresent()) {
			Track track = createTrackForFile(existing.get(), userId, request.title(), request.artist(),
					request.album());

			Upload upload = new Upload(userId, deviceId, hash, request.declaredSize(), CHUNK_SIZE_BYTES,
					request.mimeType(), request.title(), request.artist(), request.album());
			upload.setStatus(Upload.Status.DEDUP_HIT);
			upload.recordChunk(request.declaredSize());
			upload.setResultingTrackId(track.getId());
			upload.markCompleted();
			uploadRepository.save(upload);

			return new CreateUploadResponse(upload.getId(), CHUNK_SIZE_BYTES, true, track.getId());
		}

		Upload upload = new Upload(userId, deviceId, hash, request.declaredSize(), CHUNK_SIZE_BYTES,
				request.mimeType(), request.title(), request.artist(), request.album());
		upload.setTempPath(storage.tempPathFor(upload.getId()).toString());
		uploadRepository.save(upload);

		return new CreateUploadResponse(upload.getId(), CHUNK_SIZE_BYTES, false, null);
	}

	/**
	 * Writes one chunk at its fixed offset (chunkNumber * chunkSize). Idempotent:
	 * resending the same chunk number overwrites the same byte range, so a
	 * retried request after a dropped connection is always safe.
	 */
	@Transactional
	public void writeChunk(UUID userId, UUID uploadId, int chunkNumber, byte[] data) {
		Upload upload = requireInProgress(userId, uploadId);
		long offset = (long) chunkNumber * upload.getChunkSize();

		try (RandomAccessFile raf = new RandomAccessFile(upload.getTempPath(), "rw")) {
			raf.seek(offset);
			raf.write(data);
		} catch (IOException e) {
			throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to write chunk", e);
		}

		upload.recordChunk(offset + data.length);
		uploadRepository.save(upload);
	}

	@Transactional
	public CompleteUploadRequest.Response complete(UUID userId, UUID uploadId, CompleteUploadRequest request) {
		Upload upload = uploadRepository.findByIdAndUserId(uploadId, userId)
				.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Upload not found"));

		if (upload.getStatus() == Upload.Status.DEDUP_HIT) {
			// Already resolved at create-time — idempotent replay of /complete is a no-op.
			return new CompleteUploadRequest.Response(upload.getResultingTrackId(), true);
		}
		if (upload.getStatus() != Upload.Status.IN_PROGRESS) {
			throw new ResponseStatusException(CONFLICT, "Upload is not in progress: " + upload.getStatus());
		}

		Path tempFile = Path.of(upload.getTempPath());
		if (!Files.exists(tempFile)) {
			throw new ResponseStatusException(BAD_REQUEST, "No chunks received for this upload");
		}

		String actualHash;
		long actualSize;
		try {
			actualHash = storage.sha256Hex(tempFile);
			actualSize = Files.size(tempFile);
		} catch (IOException e) {
			throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to hash upload", e);
		}

		String claimedHash = request.sha256().toLowerCase();
		boolean hashesAgree = actualHash.equals(upload.getDeclaredHash()) && actualHash.equals(claimedHash);

		if (!hashesAgree) {
			storage.deleteQuietly(tempFile);
			upload.setStatus(Upload.Status.FAILED);
			uploadRepository.save(upload);
			throw new ResponseStatusException(UNPROCESSABLE_ENTITY,
					"Hash mismatch: declared=%s claimed=%s actual=%s".formatted(
							upload.getDeclaredHash(), claimedHash, actualHash));
		}

		// Re-check for a concurrent dedup winner — two uploads of the same file
		// completing at the same moment is a real race, handled below either way.
		Optional<MediaFile> existing = mediaFileRepository.findByContentHash(actualHash);
		MediaFile file;
		boolean dedupHit;

		if (existing.isPresent()) {
			storage.deleteQuietly(tempFile);
			file = existing.get();
			dedupHit = true;
		} else {
			String extension = storage.extensionFor(upload.getMimeType());
			String relativePath = storage.relativeMusicPath(actualHash, extension);
			try {
				storage.moveIntoLibrary(tempFile, relativePath);
			} catch (IOException e) {
				throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Failed to store upload", e);
			}
			try {
				file = mediaFileRepository
						.saveAndFlush(new MediaFile(actualHash, actualSize, upload.getMimeType(), relativePath));
				dedupHit = false;
			} catch (DataIntegrityViolationException raceLost) {
				// Another request won the unique-content_hash race between our
				// check and our insert — fall back to its row; our own moved
				// file was already discarded as a no-op duplicate destination.
				file = mediaFileRepository.findByContentHash(actualHash)
						.orElseThrow(() -> raceLost);
				dedupHit = true;
			}
		}

		Track track = createTrackForFile(file, userId, upload.getTitle(), upload.getArtist(), upload.getAlbum());

		upload.setStatus(Upload.Status.COMPLETED);
		upload.setResultingTrackId(track.getId());
		upload.markCompleted();
		uploadRepository.save(upload);

		return new CompleteUploadRequest.Response(track.getId(), dedupHit);
	}

	@Transactional
	public void abort(UUID userId, UUID uploadId) {
		Upload upload = uploadRepository.findByIdAndUserId(uploadId, userId)
				.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Upload not found"));

		if (upload.getStatus() == Upload.Status.COMPLETED || upload.getStatus() == Upload.Status.DEDUP_HIT) {
			throw new ResponseStatusException(CONFLICT, "Upload already completed");
		}

		if (upload.getTempPath() != null) {
			storage.deleteQuietly(Path.of(upload.getTempPath()));
		}
		upload.setStatus(Upload.Status.ABORTED);
		uploadRepository.save(upload);
	}

	private Track createTrackForFile(
			MediaFile file,
			UUID ownerId,
			String title,
			String artist,
			String album) {

		Optional<Track> existing = trackRepository.findFirstByOwnerIdAndFileIdAndDeletedAtIsNull(
				ownerId,
				file.getId());

		if (existing.isPresent()) {
			return existing.get();
		}

		Integer trackNumber = null;
		String genre = null;
		UUID artworkFileId = null;

		Optional<AudioMetadataExtractor.ExtractedMetadata> extracted = metadataExtractor
				.extract(storage.resolve(file.getStoragePath()));

		if (extracted.isPresent()) {
			AudioMetadataExtractor.ExtractedMetadata meta = extracted.get();

			if (meta.title() != null)
				title = meta.title();
			if (meta.artist() != null)
				artist = meta.artist();
			if (meta.album() != null)
				album = meta.album();
			trackNumber = meta.trackNumber();
			genre = meta.genre();

			// Only backfill the MediaFile's own audio properties the first time —
			// deterministic given the same content hash, so no point re-writing on every
			// reuse.
			if (file.getDurationMs() == null && file.getAudioCodec() == null) {
				file.setAudioProperties(meta.codec(), meta.bitrateKbps(), meta.durationMs());
				mediaFileRepository.save(file);
			}

			if (meta.hasArtwork()) {
				artworkFileId = storeArtwork(meta.artworkBytes(), meta.artworkMimeType());
			}
		}

		Track track = new Track(file.getId(), ownerId, title, artist, album);
		track.setTrackNumber(trackNumber);
		track.setGenre(genre);
		track.setArtworkFileId(artworkFileId);
		if (extracted.isPresent()) {
			track.setDurationMs(extracted.get().durationMs());
		}

		return trackRepository.save(track);
	}

	/**
	 * Content-addresses embedded artwork the same way audio files are — same dedup
	 * benefit for cover art shared across tracks.
	 */
	private UUID storeArtwork(byte[] artworkBytes, String mimeType) {
		String hash;
		try {
			hash = sha256Hex(artworkBytes);
		} catch (Exception e) {
			return null; // don't fail the whole upload over unhashable artwork bytes
		}

		Optional<MediaFile> existingArt = mediaFileRepository.findByContentHash(hash);
		if (existingArt.isPresent()) {
			return existingArt.get().getId();
		}

		String extension = storage.imageExtensionFor(mimeType);
		String relativePath = storage.relativeArtworkPath(hash, extension);
		try {
			storage.writeIfAbsent(artworkBytes, relativePath);
		} catch (IOException e) {
			return null; // artwork is a nice-to-have — never fail the track/upload over it
		}

		try {
			return mediaFileRepository.saveAndFlush(
					new MediaFile(hash, artworkBytes.length, mimeType, relativePath)).getId();
		} catch (DataIntegrityViolationException raceLost) {
			return mediaFileRepository.findByContentHash(hash).map(MediaFile::getId).orElse(null);
		}
	}

	private String sha256Hex(byte[] data) throws java.security.NoSuchAlgorithmException {
		var digest = java.security.MessageDigest.getInstance("SHA-256");
		return java.util.HexFormat.of().formatHex(digest.digest(data));
	}

	private Upload requireInProgress(UUID userId, UUID uploadId) {
		Upload upload = uploadRepository.findByIdAndUserId(uploadId, userId)
				.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Upload not found"));
		if (upload.getStatus() != Upload.Status.IN_PROGRESS) {
			throw new ResponseStatusException(CONFLICT, "Upload is not in progress: " + upload.getStatus());
		}
		return upload;
	}
}