package com.ash.resonance.auth;

import com.ash.resonance.auth.dto.LoginRequest;
import com.ash.resonance.auth.dto.LogoutRequest;
import com.ash.resonance.auth.dto.RefreshRequest;
import com.ash.resonance.auth.dto.TokenResponse;
import com.ash.resonance.device.Device;
import com.ash.resonance.device.DeviceRepository;
import com.ash.resonance.user.User;
import com.ash.resonance.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;

    public AuthController(UserRepository userRepository,
                           DeviceRepository deviceRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           RefreshTokenService refreshTokenService,
                           JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.jwtProperties = jwtProperties;
    }

    /**
     * Username/password login. Also registers the calling device (a fresh
     * "devices" row is created every call — for a single-user personal
     * server this is fine; a real multi-device-reuse story would let the
     * client pass back a known deviceId to reuse instead).
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new AuthException("Invalid username or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthException("Invalid username or password");
        }

        Device device = new Device(user.getId(), request.deviceName(), request.platform(), request.appVersion());
        String rawRefreshToken = refreshTokenService.generate();
        device.setRefreshTokenHash(refreshTokenService.hash(rawRefreshToken));
        device.setLastSeenAt(Instant.now());
        deviceRepository.save(device);

        String accessToken = jwtService.issueAccessToken(user.getId(), device.getId());
        return ResponseEntity.ok(new TokenResponse(
                accessToken,
                rawRefreshToken,
                jwtProperties.getAccessTokenTtlMinutes() * 60,
                device.getId()
        ));
    }

    /**
     * Rotates the refresh token: the presented token is single-use. A stolen,
     * already-used refresh token stops working the moment the legitimate
     * device refreshes, which is the main thing rotation buys you.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        String presentedHash = refreshTokenService.hash(request.refreshToken());

        Device device = deviceRepository
                .findByIdAndRefreshTokenHashAndRevokedAtIsNull(request.deviceId(), presentedHash)
                .orElseThrow(() -> new AuthException("Invalid or expired refresh token"));

        String newRawRefreshToken = refreshTokenService.generate();
        device.setRefreshTokenHash(refreshTokenService.hash(newRawRefreshToken));
        device.setLastSeenAt(Instant.now());
        deviceRepository.save(device);

        String accessToken = jwtService.issueAccessToken(device.getUserId(), device.getId());
        return ResponseEntity.ok(new TokenResponse(
                accessToken,
                newRawRefreshToken,
                jwtProperties.getAccessTokenTtlMinutes() * 60,
                device.getId()
        ));
    }

    /**
     * Logs out the calling device only: clears its refresh token hash so
     * no future /refresh call can succeed for it. Other devices on the
     * same account are untouched.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        deviceRepository.findById(request.deviceId()).ifPresent(device -> {
            device.setRefreshTokenHash(null);
            deviceRepository.save(device);
        });
        return ResponseEntity.noContent().build();
    }

    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }
}
