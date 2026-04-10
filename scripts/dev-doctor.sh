#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./env-android.sh
source "$SCRIPT_DIR/env-android.sh"

pass() { echo "[OK] $1"; }
fail() { echo "[ERR] $1"; }
warn() { echo "[WARN] $1"; }

if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  if "${JAVA_HOME}/bin/java" -version >/dev/null 2>&1; then
    pass "Java runtime is available (${JAVA_HOME})"
  else
    fail "JAVA_HOME is set but java runtime check failed"
  fi
else
  fail "Java 17 runtime not found"
fi

if [[ -x "${ADB_BIN:-}" ]]; then
  pass "adb is available (${ADB_BIN})"
  DEVICE_COUNT="$("$ADB_BIN" devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
  if [[ "$DEVICE_COUNT" -gt 0 ]]; then
    pass "Detected $DEVICE_COUNT connected device(s)"
  else
    warn "No connected Android device"
  fi
else
  fail "adb is not installed or not in PATH"
fi

if [[ -x ./gradlew ]]; then
  pass "Gradle wrapper is present"
else
  fail "Gradle wrapper missing"
fi

if [[ -f local.properties ]]; then
  if rg -q '^sdk.dir=' local.properties 2>/dev/null; then
    pass "local.properties has sdk.dir"
  else
    warn "local.properties exists but sdk.dir is missing"
  fi
else
  warn "local.properties missing (expected if Android SDK path not configured yet)"
fi
