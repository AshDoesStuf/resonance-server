package com.ash.resonance.device;

import com.ash.resonance.auth.AuthenticatedPrincipal;
import com.ash.resonance.device.dto.DeviceResponse;
import com.ash.resonance.device.dto.UpdateDeviceRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceRepository deviceRepository;

    public DeviceController(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    /**
     * Note: initial device registration happens inline as part of
     * POST /api/v1/auth/login for Phase 1 (see AuthController) rather than
     * as a separate pairing-code flow — that's a deliberate scope cut for a
     * single-user server; see the project notes for the tradeoff.
     */
    @GetMapping
    public List<DeviceResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return deviceRepository.findByUserIdAndRevokedAtIsNull(principal.userId()).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    @PatchMapping("/{id}")
    public DeviceResponse update(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                  @PathVariable UUID id,
                                  @RequestBody UpdateDeviceRequest request) {
        Device device = requireOwnedDevice(principal, id);

        if (request.name() != null && !request.name().isBlank()) {
            device.setName(request.name());
        }
        if (request.capabilities() != null) {
            device.setCapabilities(request.capabilities());
        }

        return DeviceResponse.from(deviceRepository.save(device));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                        @PathVariable UUID id) {
        Device device = requireOwnedDevice(principal, id);
        device.revoke();
        device.setRefreshTokenHash(null);
        deviceRepository.save(device);
        // A currently-open WebSocket for this device would be force-closed here
        // once the WS control plane exists (Phase 7) — nothing to close yet.
        return ResponseEntity.noContent().build();
    }

    private Device requireOwnedDevice(AuthenticatedPrincipal principal, UUID deviceId) {
        Device device = deviceRepository.findByIdAndRevokedAtIsNull(deviceId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Device not found"));

        if (!device.getUserId().equals(principal.userId())) {
            throw new ResponseStatusException(FORBIDDEN, "Not your device");
        }

        return device;
    }
}
