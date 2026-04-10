#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./env-android.sh
source "$SCRIPT_DIR/env-android.sh"

if [[ ! -x ./gradlew ]]; then
  echo "Run this from repository root: ./scripts/dev-mac-loop.sh"
  exit 1
fi

./gradlew :app:assembleDebug :app:testDebugUnitTest

echo "Mac loop complete: assembleDebug + testDebugUnitTest passed"
