# WhisperClient

Android client for sending speech input to a self-hosted Wispr Server and inserting/copying responses via a Flow-style floating bubble.

See the execution roadmap in [PLAN.md](./PLAN.md).
See device setup options in [docs/DEVICE_SETUP.md](./docs/DEVICE_SETUP.md).
See detailed wireless pairing/connect steps in [docs/WIRELESS_DEBUGGING_RUNBOOK.md](./docs/WIRELESS_DEBUGGING_RUNBOOK.md).
See flow-bubble design notes in [docs/FLOW_BUBBLE.md](./docs/FLOW_BUBBLE.md).

## Current Status

- M1-M3 complete: repo, Android scaffold, fast test-loop scripts.
- App currently includes:
  - launcher setup screen with server config and health check
  - in-app audio recording and `/inference` transcription flow
  - Flow-style floating bubble subsystem (decoupled from launcher screen)
  - bubble flow: tap `Mic` -> recording waveform with `✓` (submit) and `✕` (cancel)
  - focused-field insertion via accessibility, with clipboard fallback

## Prerequisites

1. JDK 17 installed (`brew install openjdk@17`).
2. Android command-line tools installed (`brew install --cask android-commandlinetools`).
3. Android SDK platform-tools installed (`sdkmanager --sdk_root=$HOME/Library/Android/sdk "platform-tools"`).
4. Android device or emulator.

The scripts source `scripts/env-android.sh`, which sets `JAVA_HOME`, prefers
`~/Library/Android/sdk/platform-tools/adb`, and auto-creates `local.properties`.

## Quick Start

```bash
./scripts/dev-e2e.sh mac
```

Then on phone/emulator:
1. Open WhisperClient app.
2. Set `Server base URL` (for your HTTPS web proxy this is `https://<mac-ip>:3000`).
3. Keep `Allow insecure HTTPS` enabled for self-signed local certs.
4. Tap `Start Recording`, speak, then tap `Stop + Transcribe`.

## Experimental Flow Bubble

The app includes an overlay subsystem that approximates a Wispr Flow-style bubble:

1. Open `Open Overlay Permission` and grant draw-over-apps permission.
2. Open `Open Accessibility Settings` and enable `WhisperClient Focus Service`.
3. Tap `Start Bubble Service`.
4. Focus a text input field in another app; the bubble appears.
5. Tap `Mic` to start recording (waveform appears).
6. Tap `✓` to submit the captured audio or `✕` to cancel.
7. If insertion is unavailable, text falls back to clipboard.
8. Drag the bubble; it snaps to screen edge and remembers position.

## Fast Loops

### Mac-only

```bash
./scripts/dev-e2e.sh mac
```

This runs local compile + unit tests without a device.

### Emulator

```bash
./scripts/dev-emulator-e2e.sh
```

Defaults to `AVD_NAME=WhisperClient_API35`. Override with:

```bash
AVD_NAME=<your_avd_name> ./scripts/dev-emulator-e2e.sh
```

### Connected Device

```bash
./scripts/dev-e2e.sh device
```

To run both local and device loops in sequence:

```bash
./scripts/dev-e2e.sh full
```

Optional log streaming during device/full mode:

```bash
TAIL_LOGCAT=1 ./scripts/dev-e2e.sh device
```

### Non-Empty Inference Test (Real WAV)

Run a real transcription integration test through the Android client networking path:

```bash
./scripts/dev-inference-test.sh https://127.0.0.1:3000 /absolute/path/to/sample.wav
```

Or via env vars:

```bash
WISPR_SERVER_URL=https://127.0.0.1:3000 \
WISPR_WAV_PATH=/absolute/path/to/sample.wav \
./scripts/dev-inference-test.sh
```

This executes `WisprServerClient.transcribeAudio(...)` in a JVM integration test and asserts a non-empty transcript.

### Server Smoke Check

```bash
./scripts/dev-server-smoke.sh https://127.0.0.1:3000
```

The `/inference` empty-body probe may return `502` in proxy mode; this is expected for this smoke check.

### Wireless ADB

```bash
./scripts/setup-wireless-adb.sh <PHONE_IP:PORT>
```

Example:

```bash
./scripts/setup-wireless-adb.sh 192.168.1.52:5555
```

### Install / Update

```bash
./scripts/dev-install.sh
```

### Logs

```bash
./scripts/dev-logcat.sh
```
