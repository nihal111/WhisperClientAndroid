# WhisperClient

Android client and keyboard (IME) for sending speech/text input to a self-hosted Wispr Server and inserting/copying responses.

See the execution roadmap in [PLAN.md](./PLAN.md).
See device setup options in [docs/DEVICE_SETUP.md](./docs/DEVICE_SETUP.md).

## Current Status

- M1 in progress: repo, Android scaffold, IME skeleton, fast test-loop scripts.
- App currently includes:
  - launcher setup screen with server config and health check
  - in-app audio recording and `/inference` transcription flow
  - IME service with `Insert` and `Copy` actions for the last transcript

## Prerequisites

1. JDK 17 installed (`brew install openjdk@17`).
2. Android command-line tools installed (`brew install --cask android-commandlinetools`).
3. Android SDK platform-tools installed (`sdkmanager --sdk_root=$HOME/Library/Android/sdk "platform-tools"`).
4. Android device with Developer Options enabled.

The scripts source `scripts/env-android.sh`, which sets `JAVA_HOME`, prefers
`~/Library/Android/sdk/platform-tools/adb`, and auto-creates `local.properties`.

## Quick Start

```bash
./scripts/dev-e2e.sh mac
```

Then on phone:
1. Open WhisperClient app.
2. Set `Server base URL` (for your HTTPS web proxy this is `https://<mac-ip>:3000`).
3. Keep `Allow insecure HTTPS` enabled for self-signed local certs.
4. Tap `Start Recording`, speak, then tap `Stop + Transcribe`.
5. Tap `Open Keyboard Settings`, enable `Whisper Keyboard`, and select it.
6. Open any text field and use keyboard `Insert` / `Copy`.

## Fast Device Loop

If you cannot connect a phone right now, use:

```bash
./scripts/dev-e2e.sh mac
```

This runs local compile + unit tests without a device.

If WhisperServer is running on your Mac, you can also smoke-check it:

```bash
./scripts/dev-server-smoke.sh https://127.0.0.1:3000
```

The `/inference` empty-body probe may return `502` in proxy mode; this is expected for this smoke check.

For connected-device iteration:

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
