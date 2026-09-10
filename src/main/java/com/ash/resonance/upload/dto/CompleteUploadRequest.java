package com.ash.resonance.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CompleteUploadRequest(
        @NotBlank @Size(min = 64, max = 64) String sha256
) {
    public record Response(UUID trackId, boolean dedupHit) {
    }
}
