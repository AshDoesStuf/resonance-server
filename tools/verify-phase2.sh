#!/usr/bin/env bash
# Phase 2 verification, per the roadmap checklist:
#   - upload the same file twice, confirm the second is a dedup no-op
#   - corrupt a chunk mid-upload, confirm the hash mismatch is caught
#
# Usage:
#   BASE_URL=http://localhost:8443 USERNAME=ash PASSWORD=change-me \
#     ./tools/verify-phase2.sh /path/to/folder/of/mp3s
#
# Requires: curl, jq, sha256sum (or shasum on macOS), dd, stat.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8443}"
USERNAME="${USERNAME:?set USERNAME}"
PASSWORD="${PASSWORD:?set PASSWORD}"
MP3_DIR="${1:?usage: $0 <folder-of-mp3s>}"
CHUNK_SIZE=$((4 * 1024 * 1024))

sha256_of() {
  if command -v sha256sum >/dev/null; then sha256sum "$1" | awk '{print $1}'
  else shasum -a 256 "$1" | awk '{print $1}'; fi
}
size_of() {
  stat -c%s "$1" 2>/dev/null || stat -f%z "$1"
}

echo "== Logging in =="
LOGIN_JSON=$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"deviceName\":\"verify-phase2\",\"platform\":\"DESKTOP\"}")
ACCESS_TOKEN=$(jq -r .accessToken <<<"$LOGIN_JSON")
AUTH=(-H "Authorization: Bearer $ACCESS_TOKEN")
echo "  ok"

# Uploads $1 (path) with title $2, prints the resulting JSON from /complete.
do_upload() {
  local file="$1" title="$2" corrupt_chunk="${3:-}"
  local hash size chunk_count upload_json upload_id server_chunk_size

  hash=$(sha256_of "$file")
  size=$(size_of "$file")

  upload_json=$(curl -sf -X POST "$BASE_URL/api/v1/uploads" "${AUTH[@]}" \
    -H 'Content-Type: application/json' \
    -d "{\"declaredSize\":$size,\"declaredHash\":\"$hash\",\"mimeType\":\"audio/mpeg\",\"title\":\"$title\"}")

  upload_id=$(jq -r .uploadId <<<"$upload_json")
  server_chunk_size=$(jq -r .chunkSize <<<"$upload_json")

  if [[ "$(jq -r .dedupHit <<<"$upload_json")" == "true" ]]; then
    echo "$upload_json"
    return
  fi

  chunk_count=$(( (size + server_chunk_size - 1) / server_chunk_size ))
  for ((n = 0; n < chunk_count; n++)); do
    local tmp_chunk
    tmp_chunk=$(mktemp)
    dd if="$file" of="$tmp_chunk" bs="$server_chunk_size" skip="$n" count=1 2>/dev/null

    if [[ "$n" == "$corrupt_chunk" ]]; then
      # Flip a byte so this chunk's content no longer matches the real file.
      printf '\xFF' | dd of="$tmp_chunk" bs=1 seek=0 count=1 conv=notrunc 2>/dev/null
    fi

    curl -sf -X PUT "$BASE_URL/api/v1/uploads/$upload_id/chunks/$n" "${AUTH[@]}" \
      -H 'Content-Type: application/octet-stream' \
      --data-binary "@$tmp_chunk" >/dev/null
    rm -f "$tmp_chunk"
  done

  curl -s -X POST "$BASE_URL/api/v1/uploads/$upload_id/complete" "${AUTH[@]}" \
    -H 'Content-Type: application/json' \
    -d "{\"sha256\":\"$hash\"}" -w '\n%{http_code}'
}

echo
echo "== Uploading every MP3 in $MP3_DIR =="
FIRST_FILE=""
for f in "$MP3_DIR"/*.mp3; do
  [[ -f "$f" ]] || continue
  [[ -z "$FIRST_FILE" ]] && FIRST_FILE="$f"
  echo "-- $(basename "$f")"
  do_upload "$f" "$(basename "$f" .mp3)"
  echo
done

if [[ -n "$FIRST_FILE" ]]; then
  echo "== Re-uploading the first file — should be a dedup no-op (dedupHit: true, zero bytes sent) =="
  RESULT=$(do_upload "$FIRST_FILE" "$(basename "$FIRST_FILE" .mp3) (dup)")
  echo "$RESULT" | jq . 2>/dev/null || echo "$RESULT"
  echo

  echo "== Corrupting chunk 0 mid-upload on fresh (never-before-seen) content — /complete should reject with 422 =="
  echo "   (using synthetic random bytes here, not a real MP3 — Phase 2 doesn't validate audio content,"
  echo "    and it has to be content whose hash isn't already in the library, or this would just be another dedup hit)"
  FRESH_FILE=$(mktemp)
  head -c "$((CHUNK_SIZE * 2 + 12345))" /dev/urandom > "$FRESH_FILE"
  RESULT=$(do_upload "$FRESH_FILE" "corruption-test" 0)
  echo "$RESULT"
  rm -f "$FRESH_FILE"
  echo
fi

echo "== Current library =="
curl -sf "$BASE_URL/api/v1/library/tracks" "${AUTH[@]}" | jq
