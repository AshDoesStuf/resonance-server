package com.ash.resonance.library.dto;

import java.time.Instant;
import java.util.UUID;

public record LibraryTrackProjection(
    UUID id,
    String title,
    String artist,
    String album,
    Long durationMs,
    String contentHash,
    Instant updatedAt,
    UUID fileId,    
    Instant deletedAt) {
}