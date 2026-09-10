package com.ash.resonance.library.dto;

import java.util.UUID;

public record TrackHashProjection(
    UUID trackId,
    String contentHash) {
}
