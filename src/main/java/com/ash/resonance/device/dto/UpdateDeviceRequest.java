package com.ash.resonance.device.dto;

import java.util.Map;

public record UpdateDeviceRequest(
        String name,
        Map<String, Object> capabilities
) {
}
