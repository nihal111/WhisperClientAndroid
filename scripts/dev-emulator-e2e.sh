#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./env-android.sh
source "$SCRIPT_DIR/env-android.sh"

AVD_NAME="${AVD_NAME:-WhisperClient_API35}"
APP_ID_DEBUG="com.wispr.client.debug"
IME_ID="$APP_ID_DEBUG/com.wispr.client.ime.WisprInputMethodService"

EMULATOR_BIN="${ANDROID_SDK_ROOT}/emulator/emulator"

if [[ ! -x "$EMULATOR_BIN" ]]; then
  echo "Android emulator binary not found at: $EMULATOR_BIN"
  echo "Install with: sdkmanager --sdk_root=\"$ANDROID_SDK_ROOT\" \"emulator\""
  exit 1
fi

if [[ ! -x "$ADB_BIN" ]]; then
  echo "adb not found at: $ADB_BIN"
  exit 1
fi

if [[ "$("$ADB_BIN" devices | awk 'NR>1 && $1 ~ /^emulator-/ && $2=="device" {count++} END {print count+0}')" -lt 1 ]]; then
  echo "[emulator-e2e] Booting AVD: $AVD_NAME"
  nohup "$EMULATOR_BIN" -avd "$AVD_NAME" -no-window -no-audio >/tmp/whisperclient-emulator.log 2>&1 &
  "$ADB_BIN" wait-for-device
  "$ADB_BIN" shell getprop sys.boot_completed | tr -d '\r' | grep -q "1" || true
  until [[ "$("$ADB_BIN" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    sleep 2
  done
else
  echo "[emulator-e2e] Reusing running emulator"
fi

echo "[emulator-e2e] Building and installing debug app"
./gradlew :app:installDebug

echo "[emulator-e2e] Granting microphone permission"
"$ADB_BIN" shell pm grant "$APP_ID_DEBUG" android.permission.RECORD_AUDIO || true

echo "[emulator-e2e] Launching app and selecting IME"
"$ADB_BIN" shell am start -n "$APP_ID_DEBUG/com.wispr.client.MainActivity"
"$ADB_BIN" shell ime enable "$IME_ID"
"$ADB_BIN" shell ime set "$IME_ID"

echo "[emulator-e2e] Running instrumented tests"
./gradlew :app:connectedDebugAndroidTest

echo "[emulator-e2e] Completed"
