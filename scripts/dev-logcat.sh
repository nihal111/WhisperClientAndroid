#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./env-android.sh
source "$SCRIPT_DIR/env-android.sh"

if [[ ! -x "${ADB_BIN:-}" ]]; then
  echo "adb not found. Install Android SDK platform-tools."
  exit 1
fi

"$ADB_BIN" logcat -v time WisprIme:I AndroidRuntime:E ActivityManager:I *:S
