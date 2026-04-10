# WhisperClient

Android client and keyboard (IME) for sending speech/text input to a self-hosted Wispr Server and inserting/copying responses.

See the execution roadmap in [PLAN.md](./PLAN.md).
See device setup options in [docs/DEVICE_SETUP.md](./docs/DEVICE_SETUP.md).
See flow-bubble design notes in [docs/FLOW_BUBBLE.md](./docs/FLOW_BUBBLE.md).

## Current Status

- M1-M3 complete: repo, Android scaffold, IME, fast test-loop scripts.
- App currently includes:
  - launcher setup screen with server config and health check
  - in-app audio recording and `/inference` transcription flow
  - IME service with direct record/transcribe plus `Insert` and `Copy` actions
  - clipboard fallback when direct insertion is unavailable
  - experimental Flow-style floating bubble subsystem (decoupled from main screen/IME path)

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
5. Tap `Open Keyboard Settings`, enable `Whisper Keyboard`, and select it.
6. Open any text field and use keyboard `Record` (then `Stop`) to transcribe directly into the focused field.
7. Use keyboard `Insert` or `Copy` to reuse the last transcript.

## Experimental Flow Bubble

The app now includes a separate `overlay` subsystem that approximates a Wispr Flow-style bubble:

1. Open `Open Overlay Permission` and grant draw-over-apps permission.
2. Open `Open Accessibility Settings` and enable `WhisperClient Focus Service`.
3. Tap `Start Bubble Service`.
4. Focus a text input field in another app; the bubble appears.
5. Tap `Record` then `Stop` to transcribe and insert text.
6. If insertion is unavailable, text falls back to clipboard.

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

### Restart IME Process

```bash
./scripts/dev-restart-ime.sh
```

### Enable/Select Whisper IME from Mac

```bash
./scripts/dev-set-ime.sh
```
