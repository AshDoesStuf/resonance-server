package com.ash.resonance.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record LogoutRequest(
        @NotNull UUID deviceId
) {
}
