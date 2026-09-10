-- Phase 1: extensions, a UUIDv7 generator, users, devices.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Time-ordered UUIDv7 (draft RFC 9562). Sortable like an autoincrement,
-- globally unique like a UUID. Used as the default PK generator for every
-- syncable table going forward.
CREATE OR REPLACE FUNCTION uuid_generate_v7()
RETURNS uuid
AS $$
DECLARE
  unix_ts_ms bytea;
  rand_bytes bytea;
  result     bytea;
BEGIN
  unix_ts_ms := substring(int8send(floor(extract(epoch FROM clock_timestamp()) * 1000)::bigint) FROM 3 FOR 6);
  rand_bytes := gen_random_bytes(10);

  result := unix_ts_ms || rand_bytes;

  -- Set version (7) in byte 6, high nibble.
  result := set_byte(result, 6, (get_byte(result, 6) & 15) | 112);
  -- Set variant (10xx) in byte 8, high 2 bits.
  result := set_byte(result, 8, (get_byte(result, 8) & 63) | 128);

  RETURN encode(result, 'hex')::uuid;
END;
$$ LANGUAGE plpgsql VOLATILE;

CREATE TABLE users (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  username      TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,          -- Argon2id
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE devices (
  id                  UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name                TEXT NOT NULL,
  platform            TEXT NOT NULL,                 -- ANDROID | WEB | DESKTOP
  app_version         TEXT,
  capabilities        JSONB NOT NULL DEFAULT '{}',
  refresh_token_hash  TEXT,                          -- SHA-256 of current refresh token, rotated on use
  last_seen_at        TIMESTAMPTZ,
  revoked_at          TIMESTAMPTZ,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_devices_user ON devices(user_id) WHERE revoked_at IS NULL;
