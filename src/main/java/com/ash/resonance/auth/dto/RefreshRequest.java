package com.ash.resonance.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RefreshRequest(
        @NotNull UUID deviceId,
        @NotBlank String refreshToken
) {
}
