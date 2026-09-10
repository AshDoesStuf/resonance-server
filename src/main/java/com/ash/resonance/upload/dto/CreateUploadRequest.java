package com.ash.resonance.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateUploadRequest(
        @Positive long declaredSize,
        @NotBlank @Size(min = 64, max = 64) String declaredHash,   // SHA-256 hex, client-computed
        @NotBlank String mimeType,
        @NotBlank String title,
        String artist,
        String album
) {
}
