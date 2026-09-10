package com.ash.resonance.streaming;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.ash.resonance.auth.AuthenticatedPrincipal;
import com.ash.resonance.auth.JwtService;
import com.ash.resonance.library.MediaFile;
import com.ash.resonance.library.MediaFileRepository;
import com.ash.resonance.library.Track;
import com.ash.resonance.library.TrackRepository;
import com.ash.resonance.storage.FileStorageService;

import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/v1/tracks")
public class StreamingController {

  private final TrackRepository trackRepository;
  private final MediaFileRepository mediaFileRepository;
  private final JwtService jwtService;
  private final FileStorageService storage;

  public StreamingController(TrackRepository trackRepository,
      MediaFileRepository mediaFileRepository,
      JwtService jwtService,
      FileStorageService storage) {
    this.trackRepository = trackRepository;
    this.mediaFileRepository = mediaFileRepository;
    this.jwtService = jwtService;
    this.storage = storage;
  }

  /**
   * Issues a short-lived, track-scoped stream URL. This is the "how do I
   * play this" call from the design doc (§6) — the client hits this once
   * (normal Bearer auth) and gets back a URL it can hand directly to
   * MediaItem.fromUri() / an <audio> tag with no further auth needed.
   */
  @GetMapping("/{id}/stream-url")
  public StreamUrlResponse getStreamUrl(@AuthenticationPrincipal AuthenticatedPrincipal principal,
      @PathVariable UUID id) {
    Track track = requireOwnedTrack(principal.userId(), id);
    String token = jwtService.issueStreamToken(principal.userId(), track.getId());
    String url = "/api/v1/tracks/" + id + "/stream?token=" + token;
    return new StreamUrlResponse(url, 300);
  }

  @GetMapping("/{id}/stream")
  public ResponseEntity<StreamingResponseBody> stream(@PathVariable UUID id,
      @RequestParam(required = false) String token,
      @RequestHeader(value = "Range", required = false) String range) {
    UUID authorizedUserId = resolveAuthorizedUserId(id, token);
    Track track = requireOwnedTrack(authorizedUserId, id);

    if (track.getFileId() == null) {
      throw new ResponseStatusException(NOT_FOUND, "Track has no playable file");
    }
    MediaFile file = mediaFileRepository.findById(track.getFileId())
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "File not found"));

    Path path = storage.resolve(file.getStoragePath());
    long fileSize = file.getSizeBytes();

    long start = 0;
    long end = fileSize - 1;
    boolean partial = false;

    if (range != null && range.startsWith("bytes=")) {
      String spec = range.substring(6);
      String[] parts = spec.split("-", 2);
      try {
        if (parts[0].isEmpty()) {
          // suffix range: "bytes=-500" = last 500 bytes
          long suffixLength = Long.parseLong(parts[1]);
          start = Math.max(0, fileSize - suffixLength);
          end = fileSize - 1;
        } else {
          start = Long.parseLong(parts[0]);
          if (parts.length > 1 && !parts[1].isEmpty())
            end = Long.parseLong(parts[1]);
        }
        partial = true;
      } catch (NumberFormatException ignored) {
        start = 0;
        end = fileSize - 1;
        partial = false;
      }
    }

    if (start < 0 || end >= fileSize || start > end) {
      return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
          .header("Content-Range", "bytes */" + fileSize)
          .build();
    }

    long rangeStart = start;
    long rangeEnd = end;
    long contentLength = rangeEnd - rangeStart + 1;

    StreamingResponseBody body = out -> {
      try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
        raf.seek(rangeStart);
        byte[] buf = new byte[64 * 1024];
        long remaining = contentLength;
        while (remaining > 0) {
          int read = raf.read(buf, 0, (int) Math.min(buf.length, remaining));
          if (read == -1)
            break;
          out.write(buf, 0, read);
          remaining -= read;
        }
      }
    };

    ResponseEntity.BodyBuilder responseBuilder = partial
        ? ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
        : ResponseEntity.ok();

    responseBuilder
        .header("Content-Type", file.getMimeType())
        .header("Accept-Ranges", "bytes")
        .header("Content-Length", String.valueOf(contentLength));

    if (partial) {
      responseBuilder.header("Content-Range", "bytes %d-%d/%d".formatted(rangeStart, rangeEnd, fileSize));
    }

    return responseBuilder.body(body);
  }

  /**
   * Prefers an existing Bearer-token session (e.g. curl testing with a
   * normal access token); falls back to the scoped stream token, since
   * JwtAuthFilter never populates the SecurityContext for this permitAll route
   * unless a header was actually sent.
   */
  private UUID resolveAuthorizedUserId(UUID trackId, String token) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthenticatedPrincipal principal) {
      return principal.userId();
    }

    if (token == null) {
      throw new ResponseStatusException(UNAUTHORIZED, "Missing stream token");
    }
    JwtService.StreamTokenClaims claims = jwtService.parseStreamToken(token)
        .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid or expired stream token"));
    if (!claims.trackId().equals(trackId)) {
      throw new ResponseStatusException(FORBIDDEN, "Token not valid for this track");
    }
    return claims.userId();
  }

  private Track requireOwnedTrack(UUID userId, UUID trackId) {
    Track track = trackRepository.findById(trackId)
        .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Track not found"));
    if (!track.getOwnerId().equals(userId) || track.isDeleted()) {
      throw new ResponseStatusException(FORBIDDEN, "Not your track");
    }
    return track;
  }

  public record StreamUrlResponse(String url, int expiresInSeconds) {
  }
}