# Whisper Client Android

Minimal Android client for dictation against a self-hosted Wispr server.

The app shows a floating dictation bubble when a text field is focused. You tap the bubble, speak, and the transcript is inserted into the active field (with clipboard fallback).

## What This App Does

- Connects to a Wispr server running on your own computer (for example, a Mac).
- Records microphone audio on-device and uploads it to `POST /inference`.
- Shows an overlay bubble only when an editable text target is active.
- Inserts transcription into the focused field through Accessibility APIs.
- Falls back to clipboard copy/paste if direct insertion is unavailable.

## How The Self-Hosted Server Setup Works (Mac + Phone + Tailscale)

This app is designed for a personal server workflow:

1. You run the Wispr server on your computer (for example, Mac) and expose a port (commonly `3000`).
2. Your Android phone is configured with that server base URL in the app.
3. If phone and computer are not on the same local Wi-Fi, Tailscale can put both devices on the same tailnet.
4. The phone then reaches the server over the Tailscale IP/hostname + port.

Example base URL patterns:

- `https://<mac-tailscale-ip>:3000`
- `https://<mac-tailnet-name>.tailnet.ts.net:3000`
- `http://<mac-tailscale-ip>:3000` (allowed by current network config)

Notes:

- `Check Server` in the app performs a GET to `/`.
- Dictation calls `POST /inference` with `multipart/form-data` and a WebM/Opus audio file.
- For local/self-signed HTTPS, `Allow insecure HTTPS` enables trust-all TLS behavior in the client.

## 10,000-Foot Architecture

### 1. Launcher / Setup UI

`MainActivity` provides:

- Server URL + insecure HTTPS toggle
- Server health check
- Manual record/transcribe test flow
- Overlay + accessibility setup actions
- Start/stop bubble service controls

### 2. Floating Bubble Service

`WisprFloatingBubbleService`:

- Owns the overlay UI (`TYPE_APPLICATION_OVERLAY`)
- Handles bubble states: idle, recording, transcribing
- Records audio with `MediaRecorder` (WebM/Opus)
- Sends audio to `WisprServerClient`
- Inserts transcript via accessibility service instance, clipboard fallback otherwise
- Supports drag, edge snap, and persisted bubble position

### 3. Focus Detection + Text Insertion

`WisprFocusAccessibilityService`:

- Watches accessibility focus/content/window events
- Determines whether an editable field is active
- Applies visibility policy (hide on sensitive fields/packages)
- Signals bubble service to show/hide
- Performs text insertion in the currently focused input node

### 4. Networking Layer

`WisprServerClient`:

- `healthCheck(baseUrl)` -> GET `/`
- `transcribeAudio(baseUrl, audioFile)` -> POST `/inference`
- Parses `{"text": "..."}` responses (with fallbacks)

### 5. Local State

SharedPreferences-backed stores:

- `ServerConfigStore`: base URL + insecure HTTPS flag
- `TranscriptStore`: last transcript
- `OverlayConfigStore`: bubble position + keyboard visibility override

## Permissions And Special Access

Manifest/runtime + special access used by the current app:

- `INTERNET`: call Wispr server endpoints
- `RECORD_AUDIO`: capture dictation audio
- `SYSTEM_ALERT_WINDOW`: render floating bubble above other apps
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE`: keep recording/transcribing service active
- `POST_NOTIFICATIONS`: show foreground-service notification status (Android 13+)
- Accessibility Service enablement (`BIND_ACCESSIBILITY_SERVICE`): detect focus + insert text

## How It Is Wired Together (End-to-End)

1. User enables overlay + accessibility permissions and starts bubble service.
2. Accessibility service detects editable focus in another app.
3. Bubble service shows the floating mic bubble.
4. User taps mic -> audio recording starts.
5. User taps submit (`✓`) -> recording stops, audio sent to Wispr server `/inference`.
6. Transcript is returned and saved.
7. App inserts transcript into focused field; if insertion fails, it copies to clipboard.
8. Bubble hides when focus is lost (unless currently recording).

## Current Scope

This is intentionally a minimal client centered on the floating dictation bubble and self-hosted server connectivity.

---

Additional internal docs:

- `docs/FLOW_BUBBLE.md`
- `docs/DEVICE_SETUP.md`
- `docs/WIRELESS_DEBUGGING_RUNBOOK.md`
