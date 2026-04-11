#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./env-android.sh
source "$SCRIPT_DIR/env-android.sh"

if [[ ! -x "${ADB_BIN:-}" ]]; then
  echo "adb not found. Install Android SDK platform-tools."
  exit 1
fi

IME_ID="com.wispr.client.debug/com.wispr.client.ime.WhisperInputMethodService"
"$ADB_BIN" shell ime enable "$IME_ID"
"$ADB_BIN" shell ime set "$IME_ID"

printf 'IME enabled and selected: %s\n' "$IME_ID"
