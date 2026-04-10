# WhisperClient Plan

This repository tracks an Android client that captures speech, sends it to Wispr Server running on a Mac (`:3000`), receives text, and either inserts text into the focused field or copies it to clipboard.

## Product Goal

Build an Android app with a fast local test loop that behaves like Wispr Flow, but targets a self-hosted Mac server endpoint.

## Architecture Decisions

1. App type: custom Android IME (`InputMethodService`) so it can insert text into focused fields.
2. UI stack: Kotlin + Jetpack Compose for settings/control surfaces.
3. Transport: HTTP/HTTPS request to configurable server URL (default: `http://<mac-lan-ip>:3000`).
4. Output: IME insert (`commitText`) and clipboard fallback mode.
5. Build flavoring: debug local-first workflow.

## Milestones

### M1. Repo + Fast Dev Loop (done)

- Create standalone repo (`~/Code/WhisperClient`).
- Scaffold Android app and baseline modules.
- Add scripts for rapid iteration:
  - install/update debug app (`adb install -r` path via Gradle install task)
  - stream logs (`adb logcat` filtered)
  - restart IME process
- Document wireless ADB setup.
- Exit criteria:
  - One-command debug install/update loop on connected phone.
  - Team can run local app and view logs quickly.

### M2. Networking Contract + Health Check (in progress)

- Define explicit request/response JSON schema with Wispr Server.
- Implement API client + timeout/retry policy.
- Add health-check action and connection status in app.
- Exit criteria:
  - App can hit `/health` and display status.
  - Sample request returns and renders text.

### M3. Non-IME Prototype Screen

- Build quick UI for manual testing:
  - record/send trigger (or typed test payload initially)
  - response rendering
  - copy button
- Exit criteria:
  - Confirm round-trip phone -> Mac `:3000` -> response visible on phone.

### M4. IME MVP

- Implement minimal keyboard service.
- Add actions: send audio/text, insert response, copy response.
- Add mode toggle: `Insert` vs `Copy only`.
- Exit criteria:
  - Returned text inserts into common apps (Notes, browser field, chat input).

### M5. Audio Capture and Request Path

- Implement mic capture, runtime permission flow, and request packaging.
- Support upload/stream mode consistent with server API.
- Exit criteria:
  - Speak -> server transcription -> insert/copy flow end-to-end.

### Current implemented subset

- Android debug tooling scripts are in place (`dev-doctor`, `dev-install`, `dev-logcat`, `setup-wireless-adb`).
- Local environment bootstrap script auto-configures `JAVA_HOME`, SDK path, and `local.properties`.
- Launcher screen supports:
  - server base URL save
  - server reachability check
  - microphone recording and upload to `/inference`
  - transcript display and copy
- IME reads and inserts/copies the most recent transcript.

### M6. Debug UX for Iteration

- Add in-app settings for endpoint override and test actions.
- Add debug flavor defaults and persistence of selected endpoint.
- Exit criteria:
  - Endpoint can be changed without rebuilding.
  - Switching between local/staging is fast.

### M7. Reliability + User Experience Hardening

- Add robust error surfaces, cancellation, and retry logic.
- Handle no-network, server timeout, malformed response.
- Improve keyboard-state transitions and latency feedback.
- Exit criteria:
  - Predictable behavior in poor network conditions.

### M8. Packaging and Distribution

- Define release process (debug/internal).
- Keep stable `applicationId` and signing strategy for replace-install updates.
- Evaluate Firebase App Distribution / internal sharing when needed.
- Exit criteria:
  - Repeatable test deployment process for rapid feature validation.

## Test Loop Targets

- Local iterative loop target: code change -> install/update -> test in under 60 seconds.
- Prefer wireless ADB to avoid cable friction.
- Keep a stable installed debug app and replace in place.

## Initial Risks

1. IME permissions and user enablement flow can be confusing; must add clear onboarding.
2. Phone-to-Mac connectivity can fail if LAN IP changes; endpoint override required from day one.
3. Audio payload format must be aligned tightly with server expectations to avoid iteration delays.
4. Android background and microphone restrictions vary by OS version; test on target device early.

## Working Style

- Ship in small commits by milestone.
- Keep docs and scripts up to date with actual command flow.
- Validate each increment with an executable check where possible.
