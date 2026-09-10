-- Embedded album art extracted from uploaded audio files gets stored as its
-- own content-addressed file (same dedup benefit as audio — the same cover
-- art appearing on many tracks is stored once).

ALTER TABLE tracks ADD COLUMN artwork_file_id UUID REFERENCES files(id);
