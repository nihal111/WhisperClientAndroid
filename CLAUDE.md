# WhisperClient

Android client for Whisper Flow — voice-to-text via a floating overlay bubble.

## Development Feedback Loop

The primary workflow is: edit code, build, deploy to the physical phone over wireless ADB, and test. **The WhisperServer backend must be running or transcription will not work.**

### 1. Ensure WhisperServer is running

The backend lives at `../WhisperServer` (i.e. `~/Code/WhisperServer`). It has two processes:

- **whisper-server** (port 8080) — whisper.cpp inference engine (Metal GPU)
- **HTTPS proxy** (port 3000) — proxies `/inference` requests to 8080

Check if they're running:

```bash
lsof -iTCP:8080 -sTCP:LISTEN
lsof -iTCP:3000 -sTCP:LISTEN
```

If either is not running, start them:

```bash
cd ~/Code/WhisperServer
./start.sh &          # inference server on port 8080
./serve-web.sh &      # HTTPS proxy on port 3000
```

The Android client connects to `https://100.89.5.62:3000/inference`. The phone must be able to reach this Mac's IP.

### 2. Build and deploy to phone

```bash
source ./scripts/env-android.sh
./gradlew :app:installDebug
"$ADB_BIN" -s 100.110.240.42:42533 shell am start -n com.wispr.client.debug/com.wispr.client.MainActivity
```

Or as a single command:

```bash
source ./scripts/env-android.sh && ./gradlew :app:installDebug && "$ADB_BIN" -s 100.110.240.42:42533 shell am start -n com.wispr.client.debug/com.wispr.client.MainActivity
```

### 3. Test on phone

1. Open any app with a text field and tap it — bubble should appear
2. Tap Mic — should start recording (waveform + check/cross buttons)
3. Tap check — sends audio to server, transcribes, pastes text into field
4. Tap cross — discards recording
5. Drag bubble — should move and snap to edge

### 4. View logs

```bash
source ./scripts/env-android.sh
"$ADB_BIN" -s 100.110.240.42:42533 logcat -s WhisperOverlaySvc:* WhisperA11y:* WhisperIME:*
```

## Wireless ADB

The phone (Samsung S24 Ultra) is connected over wireless ADB at `100.110.240.42:42533`. Always use `-s 100.110.240.42:42533` to target it explicitly — an mDNS alias auto-reconnects and causes "more than one device" errors. Disconnect it with:

```bash
"$ADB_BIN" disconnect adb-R5CW41XCM9W-WtbXau._adb-tls-connect._tcp
```

If the connection drops, reconnect without re-pairing:

```bash
source ./scripts/env-android.sh
"$ADB_BIN" connect <IP:ADB_PORT>
```

If that fails, re-pair using the full flow in `docs/WIRELESS_DEBUGGING_RUNBOOK.md`.

## Debug Package

Debug builds use package `com.wispr.client.debug`, not `com.wispr.client`. Use the debug package name in all adb commands.

## Project Structure

- `app/src/main/java/com/wispr/client/overlay/` — Floating bubble overlay (core feature)
- `app/src/main/java/com/wispr/client/ime/` — Input method service
- `app/src/main/java/com/wispr/client/network/` — Whisper server client
- `app/src/main/java/com/wispr/client/data/` — Persistence (server config, transcripts)
- `scripts/` — Dev workflow scripts (env, install, launch, logcat, etc.)
- `docs/` — Wireless debugging runbook, device setup, bubble design
