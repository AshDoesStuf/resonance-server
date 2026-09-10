package com.ash.resonance.storage;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Everything on disk lives under `resonance.storage.root`:
 *
 * uploads/<uploadId>.part — temp files during chunked upload, deleted on
 * completion (moved) or abort/failure
 * music/<ab>/<cd>/<hash>.<ext> — content-addressed by SHA-256, sharded two
 * levels deep so no directory ever holds more
 * than a few thousand entries
 *
 * The filename in music/ IS the hash — dedup is "does this path already
 * exist", nothing fancier. Callers never build a path from anything the
 * client sent directly; only from a hash this service already validated.
 */
@Service
public class FileStorageService {

    private static final Map<String, String> EXTENSION_BY_MIME_TYPE = Map.ofEntries(
            Map.entry("audio/mpeg", "mp3"),
            Map.entry("audio/mp3", "mp3"),
            Map.entry("audio/flac", "flac"),
            Map.entry("audio/x-flac", "flac"),
            Map.entry("audio/opus", "opus"),
            Map.entry("audio/ogg", "ogg"),
            Map.entry("audio/mp4", "m4a"),
            Map.entry("audio/x-m4a", "m4a"),
            Map.entry("audio/aac", "aac"),
            Map.entry("audio/wav", "wav"),
            Map.entry("audio/x-wav", "wav"));
    private static final Map<String, String> IMAGE_EXTENSION_BY_MIME_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final Path root;
    private final Path uploadsDir;
    private final Path musicDir;

    public FileStorageService(StorageProperties properties) throws IOException {
        this.root = Paths.get(properties.getRoot()).toAbsolutePath().normalize();
        this.uploadsDir = root.resolve("uploads");
        this.musicDir = root.resolve("music");
        Files.createDirectories(uploadsDir);
        Files.createDirectories(musicDir);
    }

    public Path tempPathFor(UUID uploadId) {
        return uploadsDir.resolve(uploadId + ".part");
    }

    public String extensionFor(String mimeType) {
        return EXTENSION_BY_MIME_TYPE.getOrDefault(mimeType, "bin");
    }

    public String imageExtensionFor(String mimeType) {
        return IMAGE_EXTENSION_BY_MIME_TYPE.getOrDefault(mimeType, "img");
    }

    /**
     * Relative path (from the storage root) a file with this hash/extension would
     * live at. Doesn't touch disk.
     */
    public String relativeMusicPath(String contentHashHex, String extension) {
        String a = contentHashHex.substring(0, 2);
        String b = contentHashHex.substring(2, 4);
        return "music/%s/%s/%s.%s".formatted(a, b, contentHashHex, extension);
    }

    public String relativeArtworkPath(String contentHashHex, String extension) {
        String a = contentHashHex.substring(0, 2);
        String b = contentHashHex.substring(2, 4);
        return "artwork/%s/%s/%s.%s".formatted(a, b, contentHashHex, extension);
    }

    public Path resolve(String relativePath) {
        return root.resolve(relativePath).normalize();
    }

    /**
     * Moves a completed temp upload into its final content-addressed location.
     * Idempotent: if the destination already exists (a concurrent dedup winner),
     * the temp file is just discarded.
     */
    public void moveIntoLibrary(Path tempFile, String relativePath) throws IOException {
        Path destination = resolve(relativePath);
        Files.createDirectories(destination.getParent());
        try {
            Files.move(tempFile, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException e) {
            Files.deleteIfExists(tempFile);
        }
    }

    public void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best-effort cleanup — an orphaned .part file is harmless and can be swept
            // later.
        }
    }

    /** Streaming SHA-256 of a file already on disk, hex-encoded lowercase. */
    public String sha256Hex(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 is always available
        }
    }

    /**
     * Content-addresses arbitrary bytes already in memory (as opposed to
     * moveIntoLibrary, which moves an existing temp file). Used for embedded
     * album art extracted from an uploaded audio file. No-op if the
     * destination already exists.
     */
    public void writeIfAbsent(byte[] data, String relativePath) throws IOException {
        Path destination = resolve(relativePath);
        if (Files.exists(destination)) {
            return;
        }
        Files.createDirectories(destination.getParent());
        Path tmp = Files.createTempFile(destination.getParent(), "content-", ".tmp");
        try {
            Files.write(tmp, data, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException e) {
            Files.deleteIfExists(tmp);
        }
    }
}
