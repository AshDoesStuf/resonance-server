-- Phase 2: library core — content-addressed files, tracks, resumable uploads.

CREATE TABLE files (
  id            UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  content_hash  CHAR(64) NOT NULL UNIQUE,     -- SHA-256 hex, the dedup key
  size_bytes    BIGINT NOT NULL,
  mime_type     TEXT NOT NULL,
  storage_path  TEXT NOT NULL,                -- relative to the storage root, e.g. music/ab/cd/<hash>.mp3
  audio_codec   TEXT,
  bitrate_kbps  INT,
  duration_ms   BIGINT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tracks (
  id              UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  file_id         UUID REFERENCES files(id),      -- null once YT-sourced tracks exist (Phase 6) and aren't downloaded server-side
  source          TEXT NOT NULL DEFAULT 'LOCAL_UPLOAD',  -- LOCAL_UPLOAD | YOUTUBE
  external_id     TEXT,                            -- YT videoId, unused until Phase 6
  title           TEXT NOT NULL,
  artist          TEXT,
  album           TEXT,
  track_number    INT,
  genre           TEXT,
  duration_ms     BIGINT,
  artwork_file_id UUID REFERENCES files(id),
  owner_id        UUID NOT NULL REFERENCES users(id),
  version         INT NOT NULL DEFAULT 1,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_tracks_owner ON tracks(owner_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_tracks_file ON tracks(file_id);
CREATE INDEX idx_tracks_fts ON tracks USING GIN (
  to_tsvector('simple', coalesce(title,'') || ' ' || coalesce(artist,'') || ' ' || coalesce(album,''))
);

CREATE TABLE uploads (
  id                 UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  user_id            UUID NOT NULL REFERENCES users(id),
  device_id          UUID REFERENCES devices(id),
  declared_hash      CHAR(64) NOT NULL,           -- client-computed SHA-256, checked for a dedup hit before any bytes move
  declared_size      BIGINT NOT NULL,
  chunk_size         INT NOT NULL,
  received_bytes     BIGINT NOT NULL DEFAULT 0,
  status             TEXT NOT NULL DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS | COMPLETED | FAILED | ABORTED | DEDUP_HIT
  temp_path          TEXT,                        -- null for a DEDUP_HIT upload, since no bytes are ever written
  mime_type          TEXT NOT NULL,
  title              TEXT NOT NULL,
  artist             TEXT,
  album              TEXT,
  resulting_track_id UUID REFERENCES tracks(id),
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at       TIMESTAMPTZ
);

CREATE INDEX idx_uploads_user ON uploads(user_id);
