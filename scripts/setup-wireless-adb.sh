#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <PHONE_IP:PORT>"
  exit 1
fi

TARGET="$1"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not installed or not in PATH."
  exit 1
fi

adb connect "$TARGET"
adb devices
