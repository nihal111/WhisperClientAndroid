# WhisperClient

Android client and keyboard (IME) for sending speech/text input to a self-hosted Wispr Server and inserting/copying responses.

See the execution roadmap in [PLAN.md](./PLAN.md).

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
./scripts/dev-doctor.sh
./scripts/dev-install.sh
./scripts/dev-logcat.sh
```

Then on phone:
1. Open WhisperClient app.
2. Set `Server base URL` (for your HTTPS web proxy this is `https://<mac-ip>:3000`).
3. Keep `Allow insecure HTTPS` enabled for self-signed local certs.
4. Tap `Start Recording`, speak, then tap `Stop + Transcribe`.
5. Tap `Open Keyboard Settings`, enable `Whisper Keyboard`, and select it.
6. Open any text field and use keyboard `Insert` / `Copy`.

## Fast Device Loop

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
