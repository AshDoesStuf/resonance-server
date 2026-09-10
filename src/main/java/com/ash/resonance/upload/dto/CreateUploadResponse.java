package com.ash.resonance.upload.dto;

import java.util.UUID;

public record CreateUploadResponse(
        UUID uploadId,
        int chunkSize,
        boolean dedupHit,
        UUID trackId   // present only when dedupHit is true — an identical file already exists
) {
}
