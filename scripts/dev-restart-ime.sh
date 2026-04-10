#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./env-android.sh
source "$SCRIPT_DIR/env-android.sh"

if [[ ! -x "${ADB_BIN:-}" ]]; then
  echo "adb not found. Install Android SDK platform-tools."
  exit 1
fi

"$ADB_BIN" shell am force-stop com.wispr.client.debug || true
"$ADB_BIN" shell ime reset || true

echo "Requested IME process reset for com.wispr.client.debug"
