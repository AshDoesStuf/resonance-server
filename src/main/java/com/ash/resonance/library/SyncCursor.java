package com.ash.resonance.library;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * A raw timestamp cursor has a well-known bug: two rows updated in the same
 * millisecond can be skipped if the cursor is just "give me everything after
 * time T". Encoding (updatedAt, id) together and requiring the query to
 * break ties on id fixes it — see TrackRepository.findPageSinceCursor.
 */
public final class SyncCursor {

    public static final SyncCursor EPOCH = new SyncCursor(Instant.EPOCH, new UUID(0, 0));

    private final Instant updatedAt;
    private final UUID id;

    private SyncCursor(Instant updatedAt, UUID id) {
        this.updatedAt = updatedAt;
        this.id = id;
    }

    public static SyncCursor of(Instant updatedAt, UUID id) {
        return new SyncCursor(updatedAt, id);
    }

    public static SyncCursor decode(String token) {
        if (token == null || token.isBlank()) {
            return EPOCH;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new SyncCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor");
        }
    }

    public String encode() {
        String raw = updatedAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public UUID id() {
        return id;
    }
}
