#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./env-android.sh
source "$SCRIPT_DIR/env-android.sh"

SERVER_URL="${1:-${WISPR_SERVER_URL:-https://127.0.0.1:3000}}"
WAV_PATH="${2:-${WISPR_WAV_PATH:-}}"
ALLOW_INSECURE_HTTPS="${WISPR_ALLOW_INSECURE_HTTPS:-1}"

if [[ -z "$WAV_PATH" ]]; then
  echo "Usage: $0 [SERVER_URL] <WAV_PATH>"
  echo "Or set WISPR_WAV_PATH environment variable."
  exit 1
fi

if [[ ! -f "$WAV_PATH" ]]; then
  echo "WAV file does not exist: $WAV_PATH"
  exit 1
fi

if [[ "${WAV_PATH##*.}" != "wav" && "${WAV_PATH##*.}" != "WAV" ]]; then
  echo "Expected a .wav file: $WAV_PATH"
  exit 1
fi

echo "Running non-empty inference integration test"
echo "  SERVER_URL=$SERVER_URL"
echo "  WISPR_WAV_PATH=$WAV_PATH"
echo "  WISPR_ALLOW_INSECURE_HTTPS=$ALLOW_INSECURE_HTTPS"

WISPR_SERVER_URL="$SERVER_URL" \
WISPR_WAV_PATH="$WAV_PATH" \
WISPR_ALLOW_INSECURE_HTTPS="$ALLOW_INSECURE_HTTPS" \
./gradlew :app:testDebugUnitTest \
  --tests "com.wispr.client.network.WhisperServerIntegrationTest.transcribeAudio with real wav returns non-empty text"

echo "Integration test passed"
