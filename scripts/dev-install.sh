#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./env-android.sh
source "$SCRIPT_DIR/env-android.sh"

if [[ ! -x "${ADB_BIN:-}" ]]; then
  echo "adb not found. Install Android SDK platform-tools."
  exit 1
fi

if [[ ! -x ./gradlew ]]; then
  echo "Run this script from repository root: ./scripts/dev-install.sh"
  exit 1
fi

DEVICE_COUNT="$("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
if [[ "$DEVICE_COUNT" -lt 1 ]]; then
  echo "No connected Android device found. Connect via USB or wireless adb first."
  exit 1
fi

./gradlew :app:installDebug

echo "Installed/updated com.wispr.client.debug"
