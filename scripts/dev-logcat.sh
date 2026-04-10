#!/usr/bin/env bash
set -euo pipefail

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not installed or not in PATH."
  exit 1
fi

adb logcat -v time WisprIme:I AndroidRuntime:E ActivityManager:I *:S
