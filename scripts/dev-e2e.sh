#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-mac}"
SERVER_URL="${SERVER_URL:-https://127.0.0.1:3000}"
TAIL_LOGCAT="${TAIL_LOGCAT:-0}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<USAGE
Usage: ./scripts/dev-e2e.sh [mac|device|full]

Modes:
  mac     Run local compile/tests and server smoke check (default)
  device  Run device install/launch/IME selection (requires adb device)
  full    Run mac mode then device mode

Environment variables:
  SERVER_URL=<url>   Server URL for smoke check (default: https://127.0.0.1:3000)
  TAIL_LOGCAT=1      Tail logcat at end of device/full mode
USAGE
}

run_mac() {
  echo "[dev-e2e] Running mac loop..."
  "$SCRIPT_DIR/dev-mac-loop.sh"

  echo "[dev-e2e] Running server smoke check against $SERVER_URL ..."
  "$SCRIPT_DIR/dev-server-smoke.sh" "$SERVER_URL"

  echo "[dev-e2e] mac mode completed"
}

run_device() {
  echo "[dev-e2e] Checking environment/device..."
  "$SCRIPT_DIR/dev-doctor.sh"

  echo "[dev-e2e] Installing debug app..."
  "$SCRIPT_DIR/dev-install.sh"

  echo "[dev-e2e] Launching app and selecting IME..."
  "$SCRIPT_DIR/dev-launch-app.sh"
  "$SCRIPT_DIR/dev-set-ime.sh"

  if [[ "$TAIL_LOGCAT" == "1" ]]; then
    echo "[dev-e2e] Tailing logcat (CTRL+C to stop)..."
    "$SCRIPT_DIR/dev-logcat.sh"
  fi

  echo "[dev-e2e] device mode completed"
}

case "$MODE" in
  mac)
    run_mac
    ;;
  device)
    run_device
    ;;
  full)
    run_mac
    run_device
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    echo "Unknown mode: $MODE"
    usage
    exit 1
    ;;
esac
