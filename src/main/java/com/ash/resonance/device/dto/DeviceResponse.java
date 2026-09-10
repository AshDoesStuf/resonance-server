package com.ash.resonance.device.dto;

import com.ash.resonance.device.Device;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DeviceResponse(
        UUID id,
        String name,
        Device.Platform platform,
        String appVersion,
        Map<String, Object> capabilities,
        Instant lastSeenAt,
        Instant createdAt
) {
    public static DeviceResponse from(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getName(),
                device.getPlatform(),
                device.getAppVersion(),
                device.getCapabilities(),
                device.getLastSeenAt(),
                device.getCreatedAt()
        );
    }
}
