#!/usr/bin/env bash
set -euo pipefail

pass() { echo "[OK] $1"; }
fail() { echo "[ERR] $1"; }
warn() { echo "[WARN] $1"; }

if command -v java >/dev/null 2>&1; then
  if java -version >/dev/null 2>&1; then
    pass "Java runtime is available"
  else
    fail "java command exists but runtime is not configured"
  fi
else
  fail "java is not installed"
fi

if command -v adb >/dev/null 2>&1; then
  pass "adb is available"
  DEVICE_COUNT="$(adb devices | awk 'NR>1 && $2=="device" {count++} END {print count+0}')"
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
