package com.ash.resonance.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates short-lived access tokens.
 *
 * Refresh tokens are deliberately NOT JWTs — they're opaque random strings
 * whose SHA-256 hash is stored per-device (see RefreshTokenService), so a
 * device can be revoked server-side by clearing that one row without needing
 * a token blocklist.
 */
@Service
public class JwtService {

    private static final String CLAIM_DEVICE_ID = "deviceId";
    private static final String TOKEN_TYPE_CLAIM = "typ";
    private static final String TOKEN_TYPE_ACCESS = "access";

    private static final String CLAIM_TRACK_ID = "trackId";
    private static final String TOKEN_TYPE_STREAM = "stream";
    private static final Duration STREAM_TOKEN_TTL = Duration.ofMinutes(5);

    private final SecretKey key;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.getSecret()));
    }

    public String issueAccessToken(UUID userId, UUID deviceId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(properties.getAccessTokenTtlMinutes()));

        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_DEVICE_ID, deviceId.toString())
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public String issueStreamToken(UUID userId, UUID trackId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(STREAM_TOKEN_TTL);

        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TRACK_ID, trackId.toString())
                .claim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_STREAM)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * Returns the validated claims, or empty if the token is
     * missing/expired/invalid/wrong type.
     */
    public Optional<AccessTokenClaims> parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!TOKEN_TYPE_ACCESS.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                return Optional.empty();
            }

            UUID userId = UUID.fromString(claims.getSubject());
            UUID deviceId = UUID.fromString(claims.get(CLAIM_DEVICE_ID, String.class));
            return Optional.of(new AccessTokenClaims(userId, deviceId));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Returns the validated claims, or empty if missing/expired/invalid/wrong type.
     */
    public Optional<StreamTokenClaims> parseStreamToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!TOKEN_TYPE_STREAM.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                return Optional.empty();
            }

            UUID userId = UUID.fromString(claims.getSubject());
            UUID trackId = UUID.fromString(claims.get(CLAIM_TRACK_ID, String.class));
            return Optional.of(new StreamTokenClaims(userId, trackId));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record AccessTokenClaims(UUID userId, UUID deviceId) {
    }

    public record StreamTokenClaims(UUID userId, UUID trackId) {
    }
}
