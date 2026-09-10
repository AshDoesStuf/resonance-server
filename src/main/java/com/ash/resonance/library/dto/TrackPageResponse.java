package com.ash.resonance.library.dto;

import java.util.List;

public record TrackPageResponse(
        List<TrackResponse> tracks,
        String nextCursor,
        boolean hasMore
) {
}
