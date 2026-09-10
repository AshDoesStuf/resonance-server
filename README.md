# resonance-server — Phase 1

Server skeleton, auth, and device management for the [Resonance](../resonance)
Android app's companion backend. Corresponds to Phase 1 of the roadmap:
Spring Boot project, Postgres via Compose, Flyway migrations for
`users`/`devices`, and login/refresh/device endpoints.

## What's here

- Spring Boot 3.3 (Java 21), Spring Security (stateless JWT), Spring Data JPA
- PostgreSQL via Flyway migration (`V1__init.sql`): `users`, `devices`, and a
  `uuid_generate_v7()` function used as the PK scheme for every table going
  forward (time-ordered, globally unique)
- `POST /api/v1/auth/login` — username/password, registers the calling
  device inline, returns an access JWT (15 min) + opaque refresh token (30
  days, rotated on every use, stored server-side only as a SHA-256 hash)
- `POST /api/v1/auth/refresh` — rotates the refresh token
- `POST /api/v1/auth/logout` — clears the refresh token for one device
- `GET /api/v1/devices`, `PATCH /api/v1/devices/{id}`, `DELETE /api/v1/devices/{id}`
  (revoke) — all scoped to the authenticated user's own devices

**Scope cut from the original design doc:** device registration happens
inline as part of `/auth/login` rather than via a separate short-lived
pairing-code flow. For a single-user server this is simpler and just as
safe (the new device already has to present the password once); the
pairing-code flow is worth adding later specifically for a web client you
don't want to type a password into.

## Local setup

Requires Java 21 and Maven (with normal access to Maven Central — this
was written in a sandboxed environment with no outbound network, so **it
has not been compiled or run yet**. Build it locally first: `mvn clean
verify` before you rely on it.)

```bash
# 1. Start Postgres
docker compose up -d postgres

# 2. Run the app (env vars below create your one user on first boot)
SEED_USERNAME=ash SEED_PASSWORD='change-me' \
JWT_SECRET=$(openssl rand -base64 32) \
mvn spring-boot:run
```

`JWT_SECRET` must be a base64-encoded value — `openssl rand -base64 32`
gives you a fresh 256-bit one. Keep whatever you generate; changing it
invalidates every outstanding access token.

## Verify (matches the Phase 1 checklist: "can register a device and get a
valid JWT via curl")

```bash
# Log in — this also registers "My Test Client" as a device and returns tokens
curl -s -X POST http://localhost:8443/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "ash",
    "password": "change-me",
    "deviceName": "My Test Client",
    "platform": "DESKTOP"
  }' | tee /tmp/login.json

ACCESS_TOKEN=$(jq -r .accessToken /tmp/login.json)
REFRESH_TOKEN=$(jq -r .refreshToken /tmp/login.json)
DEVICE_ID=$(jq -r .deviceId /tmp/login.json)

# Use the access token on an authenticated endpoint
curl -s http://localhost:8443/api/v1/devices \
  -H "Authorization: Bearer $ACCESS_TOKEN" | jq

# Rotate via refresh
curl -s -X POST http://localhost:8443/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"deviceId\": \"$DEVICE_ID\", \"refreshToken\": \"$REFRESH_TOKEN\"}" | jq

# Revoke the device
curl -s -X DELETE "http://localhost:8443/api/v1/devices/$DEVICE_ID" \
  -H "Authorization: Bearer $ACCESS_TOKEN" -w '%{http_code}\n'

# The old refresh token should now fail
curl -s -X POST http://localhost:8443/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"deviceId\": \"$DEVICE_ID\", \"refreshToken\": \"$REFRESH_TOKEN\"}" -w '\n%{http_code}\n'
```

## Not in Phase 1 (by design)

Library, streaming, uploads, sync, WebSocket control plane, YT resolution —
all later phases per the roadmap. This phase is deliberately just enough
to prove auth + device lifecycle end-to-end before anything else is built
on top of it.
