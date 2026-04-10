#!/usr/bin/env bash
set -euo pipefail

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not installed or not in PATH."
  exit 1
fi

adb shell am force-stop com.wispr.client.debug || true
adb shell ime reset || true

echo "Requested IME process reset for com.wispr.client.debug"
