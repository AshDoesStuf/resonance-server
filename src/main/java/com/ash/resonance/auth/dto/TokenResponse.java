package com.ash.resonance.auth.dto;

import java.util.UUID;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiresInSeconds,
        UUID deviceId
) {
}
