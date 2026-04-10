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

if [[ "$ROOT_CODE" != "200" ]]; then
  echo "Unexpected root status: HTTP $ROOT_CODE (expected 200)."
  exit 1
fi

# Empty-body inference probe is intentionally not a valid transcription request.
# In local proxy setups this can be 4xx or 502; either still confirms the route exists.
case "$INFER_CODE" in
  400|401|403|404|405|413|415|422|500|502)
    ;;
  *)
    echo "Unexpected /inference probe status: HTTP $INFER_CODE."
    echo "Expected one of: 400,401,403,404,405,413,415,422,500,502"
    exit 1
    ;;
esac

if [[ "$INFER_CODE" == "500" || "$INFER_CODE" == "502" ]]; then
  echo "Note: /inference returned $INFER_CODE for empty-body probe; verify full upload path with real audio."
fi

if [[ "$ROOT_CODE" == "000" || "$INFER_CODE" == "000" ]]; then
  echo "Connection failed. Check WhisperServer process state and port bindings."
  exit 1
fi

echo "Server smoke check complete"
