#!/usr/bin/env bash
# Phase 3 verification: streaming endpoint.
#
# Usage:
#   BASE_URL=http://localhost:8443 USERNAME=ash PASSWORD=change-me \
#     ./tools/verify-phase3.sh
#
# Requires at least one track already in the library (run verify-phase2.sh
# first if you haven't). Requires: curl, jq.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8443}"
USERNAME="${USERNAME:?set USERNAME}"
PASSWORD="${PASSWORD:?set PASSWORD}"

PASS=0
FAIL=0

check() {
  local desc="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    echo "  PASS: $desc (got $actual)"
    PASS=$((PASS + 1))
  else
    echo "  FAIL: $desc (expected $expected, got $actual)"
    FAIL=$((FAIL + 1))
  fi
}

echo "== Logging in =="
LOGIN_JSON=$(curl -sf -X POST "$BASE_URL/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"deviceName\":\"verify-phase3\",\"platform\":\"DESKTOP\"}")
ACCESS_TOKEN=$(jq -r .accessToken <<<"$LOGIN_JSON")
AUTH=(-H "Authorization: Bearer $ACCESS_TOKEN")
echo "  ok"

echo
echo "== Fetching library =="
TRACKS_JSON=$(curl -sf "$BASE_URL/api/v1/library/tracks" "${AUTH[@]}")
TRACK_COUNT=$(jq '.tracks | length' <<<"$TRACKS_JSON")
if [[ "$TRACK_COUNT" -lt 1 ]]; then
  echo "No tracks in the library — run verify-phase2.sh first to upload something." >&2
  exit 1
fi
TRACK_ID=$(jq -r '.tracks[0].id' <<<"$TRACKS_JSON")
echo "  using track $TRACK_ID ($(jq -r '.tracks[0].title' <<<"$TRACKS_JSON"))"

SECOND_TRACK_ID=""
if [[ "$TRACK_COUNT" -ge 2 ]]; then
  SECOND_TRACK_ID=$(jq -r '.tracks[1].id' <<<"$TRACKS_JSON")
fi

echo
echo "== Issuing a stream URL (normal Bearer auth) =="
STREAM_URL_JSON=$(curl -sf "$BASE_URL/api/v1/tracks/$TRACK_ID/stream-url" "${AUTH[@]}")
STREAM_PATH=$(jq -r .url <<<"$STREAM_URL_JSON")
STREAM_TOKEN=$(grep -oP '(?<=token=)[^&]+' <<<"$STREAM_PATH")
echo "  $STREAM_PATH"

FULL_URL="$BASE_URL$STREAM_PATH"

echo
echo "== Full request, no Range header — expect 200, capture size =="
# Use a normal GET (not HEAD) to read Content-Length — Spring's automatic
# HEAD-for-GET support doesn't propagate Content-Length correctly for a
# StreamingResponseBody, so a HEAD request here reports a bogus 0.
FULL_HEADERS=$(curl -s -D - -o /dev/null "$FULL_URL")
CODE=$(head -1 <<<"$FULL_HEADERS" | tr -d '\r' | awk '{print $2}')
FULL_SIZE=$(grep -i '^content-length:' <<<"$FULL_HEADERS" | tr -d '\r' | awk '{print $2}')
check "no-Range request" "200" "$CODE"
echo "  file size: $FULL_SIZE bytes"

echo
echo "== Range request (bytes=1000-2000) — expect 206 with correct Content-Range =="
HEADERS=$(curl -s -D - -o /dev/null "$FULL_URL" -H "Range: bytes=1000-2000")
CODE=$(head -1 <<<"$HEADERS" | tr -d '\r' | awk '{print $2}')
CONTENT_RANGE=$(grep -i '^content-range:' <<<"$HEADERS" | sed -E 's/^[Cc]ontent-[Rr]ange:[[:space:]]*//' | tr -d '\r')
check "mid-range request status" "206" "$CODE"
check "Content-Range header" "bytes 1000-2000/$FULL_SIZE" "$CONTENT_RANGE"

echo
echo "== Suffix range request (bytes=-500, i.e. last 500 bytes) — expect 206 =="
SUFFIX_START=$((FULL_SIZE - 500))
SUFFIX_END=$((FULL_SIZE - 1))
HEADERS=$(curl -s -D - -o /dev/null "$FULL_URL" -H "Range: bytes=-500")
CODE=$(head -1 <<<"$HEADERS" | tr -d '\r' | awk '{print $2}')
CONTENT_RANGE=$(grep -i '^content-range:' <<<"$HEADERS" | sed -E 's/^[Cc]ontent-[Rr]ange:[[:space:]]*//' | tr -d '\r')
check "suffix-range request status" "206" "$CODE"
check "suffix Content-Range header" "bytes $SUFFIX_START-$SUFFIX_END/$FULL_SIZE" "$CONTENT_RANGE"

echo
echo "== No token, no Bearer header at all — expect 401 =="
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/v1/tracks/$TRACK_ID/stream")
check "unauthenticated stream request" "401" "$CODE"

echo
echo "== Garbage token — expect 401 =="
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/v1/tracks/$TRACK_ID/stream?token=not-a-real-token")
check "garbage stream token" "401" "$CODE"

if [[ -n "$SECOND_TRACK_ID" ]]; then
  echo
  echo "== Token scoped to track A, used against track B — expect 403 =="
  CROSS_URL="$BASE_URL/api/v1/tracks/$SECOND_TRACK_ID/stream?token=$STREAM_TOKEN"
  CROSS_HEADERS=$(curl -s -D - -o /tmp/verify-phase3-cross-body.txt "$CROSS_URL")
  CODE=$(head -1 <<<"$CROSS_HEADERS" | tr -d '\r' | awk '{print $2}')
  check "mismatched-track stream token" "403" "$CODE"
  if [[ "$CODE" != "403" ]]; then
    echo "  -- diagnostic: unexpected status, dumping response for inspection --"
    echo "  request: GET $CROSS_URL"
    echo "  response headers:"
    sed 's/^/    /' <<<"$CROSS_HEADERS"
    echo "  response body:"
    sed 's/^/    /' /tmp/verify-phase3-cross-body.txt
  fi
  rm -f /tmp/verify-phase3-cross-body.txt
else
  echo
  echo "== Skipping token-scoping cross-track test — need a second track in the library =="
fi

echo
echo "== stream-url endpoint itself requires normal auth — expect 401 with no Bearer header =="
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/v1/tracks/$TRACK_ID/stream-url")
check "unauthenticated stream-url request" "401" "$CODE"

echo
echo "== Results: $PASS passed, $FAIL failed =="
[[ "$FAIL" -eq 0 ]]
