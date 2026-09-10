package com.ash.resonance.library;

import com.ash.resonance.auth.AuthenticatedPrincipal;
import com.ash.resonance.library.dto.LibraryTrackProjection;
import com.ash.resonance.library.dto.TrackHashCheckRequest;
import com.ash.resonance.library.dto.TrackHashCheckResponse;
import com.ash.resonance.library.dto.TrackPageResponse;
import com.ash.resonance.library.dto.TrackResponse;
import com.ash.resonance.storage.FileStorageService;

import jakarta.validation.Valid;

import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@Valid
@RequestMapping("/api/v1/library")
public class LibraryController {

    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_PAGE_SIZE = 500;

    private final TrackRepository trackRepository;
    private final MediaFileRepository mediaFileRepository;
    private final FileStorageService storage;
    private final LibraryService libraryService;

    public LibraryController(
            TrackRepository trackRepository,
            MediaFileRepository mediaFileRepository,
            FileStorageService storage,
            LibraryService libraryService) {

        this.trackRepository = trackRepository;
        this.mediaFileRepository = mediaFileRepository;
        this.storage = storage;
        this.libraryService = libraryService;
    }

    @GetMapping("/tracks")
    public TrackPageResponse listTracks(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestParam(required = false) String since,
            @RequestParam(required = false) Integer limit) {
        SyncCursor cursor = SyncCursor.decode(since);
        int pageSize = Math.min(limit == null ? DEFAULT_PAGE_SIZE : limit, MAX_PAGE_SIZE);

        // Fetch one extra row to know whether there's a next page without a second
        // query.
        List<LibraryTrackProjection> page = trackRepository.findLibraryPageSinceCursor(
                principal.userId(),
                cursor.updatedAt(),
                cursor.id(),
                PageRequest.of(0, pageSize + 1));
        boolean hasMore = page.size() > pageSize;

        List<LibraryTrackProjection> trimmed = hasMore
                ? page.subList(0, pageSize)
                : page;

        String nextCursor = trimmed.isEmpty()
                ? since
                : SyncCursor.of(
                        trimmed.get(trimmed.size() - 1).updatedAt(),
                        trimmed.get(trimmed.size() - 1).id()).encode();

        List<TrackResponse> tracks = trimmed.stream()
                .map(t -> new TrackResponse(
                        t.id(),
                        t.title(),
                        t.artist(),
                        t.album(),
                        t.durationMs(),
                        t.contentHash(),
                        t.deletedAt() != null
                                ? t.deletedAt().toString()
                                : null))
                .toList();

        return new TrackPageResponse(
                tracks,
                nextCursor,
                hasMore);
    }

    @GetMapping("/tracks/{id}")
    public TrackResponse getTrack(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id) {
        return TrackResponse.from(requireOwnedTrack(principal, id), null);
    }

    @GetMapping("/tracks/{id}/artwork")
    public ResponseEntity<FileSystemResource> getArtwork(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID id) {
        Track track = requireOwnedTrack(principal, id);
        if (track.getArtworkFileId() == null) {
            throw new ResponseStatusException(NOT_FOUND, "Track has no artwork");
        }
        MediaFile artFile = mediaFileRepository.findById(track.getArtworkFileId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Artwork file not found"));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artFile.getMimeType()))
                .body(new FileSystemResource(storage.resolve(artFile.getStoragePath())));
    }

    @DeleteMapping("/tracks/{id}")
    public ResponseEntity<Void> deleteTrack(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID id) {
        Track track = requireOwnedTrack(principal, id);
        track.softDelete();
        trackRepository.save(track);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tracks/check")
    public TrackHashCheckResponse checkHashes(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody TrackHashCheckRequest request) {

        return libraryService.checkHashes(
                principal.userId(),
                request.hashes());
    }

    private Track requireOwnedTrack(AuthenticatedPrincipal principal, UUID id) {
        Track track = trackRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Track not found"));
        if (!track.getOwnerId().equals(principal.userId())) {
            throw new ResponseStatusException(FORBIDDEN, "Not your track");
        }
        return track;
    }
}