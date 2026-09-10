package com.ash.resonance.auth.dto;

import com.ash.resonance.device.Device;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String deviceName,
        @NotNull Device.Platform platform,
        String appVersion
) {
}
