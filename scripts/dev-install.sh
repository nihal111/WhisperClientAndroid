#!/usr/bin/env bash
set -euo pipefail

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not installed or not in PATH."
  echo "Install Android platform-tools and re-run."
  exit 1
fi

if [[ ! -x ./gradlew ]]; then
  echo "Run this script from repository root: ./scripts/dev-install.sh"
  exit 1
fi

DEVICE_COUNT="$(adb devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
if [[ "$DEVICE_COUNT" -lt 1 ]]; then
  echo "No connected Android device found. Connect via USB or wireless adb first."
  exit 1
fi

./gradlew :app:installDebug

echo "Installed/updated com.wispr.client.debug"
