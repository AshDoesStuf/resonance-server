package com.ash.resonance.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    List<Device> findByUserIdAndRevokedAtIsNull(UUID userId);

    Optional<Device> findByIdAndRevokedAtIsNull(UUID id);

    // Used on refresh: given a device id, check whether the presented refresh
    // token's hash matches the currently-stored one for that (non-revoked) device.
    Optional<Device> findByIdAndRefreshTokenHashAndRevokedAtIsNull(UUID id, String refreshTokenHash);
}
