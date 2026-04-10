#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${1:-https://127.0.0.1:3000}"
BASE_URL="${BASE_URL%/}"

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required"
  exit 1
fi

echo "Checking server reachability: $BASE_URL"
ROOT_CODE="$(curl -k -sS -o /tmp/whisperclient-root.out -w '%{http_code}' "$BASE_URL/")"
echo "GET / -> HTTP $ROOT_CODE"

INFER_CODE="$(curl -k -sS -o /tmp/whisperclient-inference.out -w '%{http_code}' -X POST "$BASE_URL/inference")"
echo "POST /inference (no body) -> HTTP $INFER_CODE"

if [[ "$ROOT_CODE" -ge 500 || "$INFER_CODE" -ge 500 ]]; then
  echo "Server responded with 5xx. Check WhisperServer logs."
  exit 1
fi

echo "Server smoke check complete"
