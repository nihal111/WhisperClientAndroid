# WhisperClient Plan

This repository tracks an Android client that captures speech, sends it to Wispr Server running on a Mac (`:3000`), receives text, and either inserts text into the focused field or copies it to clipboard.

## Product Goal

Build an Android app with a fast local test loop that behaves like Wispr Flow, but targets a self-hosted Mac server endpoint.

## Constraints

1. Device connectivity is not always available (no USB and not always on same LAN).
2. Mac-only feedback loops must stay productive without a connected phone.
3. Server contract is `/inference` multipart upload with JSON `{ "text": "..." }` response.

## Milestone Roadmap

### M1. Foundation And Repo Hygiene (completed)

- Standalone repo at `~/Code/WhisperClient`.
- Android app scaffold + IME baseline.
- Basic scripts for install, logs, IME selection, environment checks.
- `.gitignore` hardened for Android/local artifacts.

Exit criteria:
- Project builds locally.
- Debug app can be installed when a device is connected.

### M2. Core Transcription Path (completed)

- App settings for server base URL and insecure HTTPS toggle.
- In-app recording and upload to `/inference`.
- Last transcript persistence and IME insert/copy actions.

Exit criteria:
- Record -> transcribe -> transcript visible in app.
- IME can insert or copy latest transcript.

### M3. Mac-First Testing Harness (completed)

- Mac loop script (`assembleDebug + unit tests`).
- Server smoke check script for `:3000` proxy route.
- Network client unit tests for URL normalization and response parsing.
- E2E orchestrator script to run mac and device loops from one entry point.

Exit criteria:
- One command validates build + tests + server reachability on Mac.
- Optional one command performs device install/launch when connected.

### M4. Device Loop Acceleration (next)

- Tighten adb workflows (`install`, `launch`, `set ime`, optional logcat tail).
- Add explicit `no-device` and `device` execution modes to avoid blocking.
- Reduce iteration to near one-command update cycle.
- Add emulator one-command loop for automated smoke and instrumentation checks.

Exit criteria:
- From code change to test on phone in minimal steps.
- Repeatable workflow documented for USB and wireless adb.

### M5. IME Reliability And UX Hardening (next)

- Handle insertion failures robustly with clipboard fallback and user feedback.
- Add recent transcript history for quick reuse.
- Improve status/error messaging for network and permission states.

Exit criteria:
- Reliable insertion in common apps.
- Failures are understandable and recoverable.

### M6. Network Robustness (next)

- Add timeout/retry policy and retry-safe error categories.
- Improve TLS/self-signed diagnostics and host validation messaging.
- Add payload and response validation for malformed server output.

Exit criteria:
- Predictable behavior under weak/unstable network conditions.

### M7. Packaging And Internal Distribution (next)

- Define debug vs internal distribution build flavors.
- Establish signing/versioning update path for frequent tester updates.
- Document APK delivery/update process for remote device testing.

Exit criteria:
- Repeatable process to ship frequent updates to installed test app.

## Current Workstream (Active)

1. Complete M4 with emulator-first execution and tighter install/retest cycle.
2. Keep server integration checks aligned with real WhisperServer behavior.
3. Continue shipping incremental commits that preserve fast iteration.

## Immediate Task Backlog

1. Add `scripts/dev-e2e.sh` with `mac`, `device`, and `full` modes.
2. Wire `dev-e2e.sh` into `README.md` as preferred entry point.
3. Keep validating with `dev-mac-loop.sh` in no-device conditions.
4. Begin M5 with small, testable IME reliability improvements.

## Working Style

1. Ship in small commits by milestone.
2. Keep docs and scripts aligned with actual executable flow.
3. Prefer executable checks over manual assertions.
