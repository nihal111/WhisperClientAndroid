#!/usr/bin/env bash
set -euo pipefail

# Resolve repository root from the scripts directory.
SOURCE_PATH="${BASH_SOURCE[0]:-$0}"
SCRIPT_DIR="$(cd "$(dirname "$SOURCE_PATH")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Prefer Homebrew OpenJDK 17 when available.
if [[ -d "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home" ]]; then
  export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
elif command -v /usr/libexec/java_home >/dev/null 2>&1; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
fi

if [[ -n "${JAVA_HOME:-}" && -d "$JAVA_HOME/bin" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

# Default SDK root path used by Android Studio/sdkmanager.
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"

if [[ -d "$ANDROID_SDK_ROOT/platform-tools" ]]; then
  export PATH="$ANDROID_SDK_ROOT/platform-tools:$PATH"
fi

ADB_BIN="${ADB_BIN:-$ANDROID_SDK_ROOT/platform-tools/adb}"
if [[ ! -x "$ADB_BIN" ]] && command -v adb >/dev/null 2>&1; then
  ADB_BIN="$(command -v adb)"
fi
export ADB_BIN

# Create local.properties if it does not exist so Gradle can find SDK.
if [[ ! -f "$REPO_ROOT/local.properties" ]]; then
  printf 'sdk.dir=%s\n' "${ANDROID_SDK_ROOT//\//\\/}" > "$REPO_ROOT/local.properties"
fi
