# Flow Bubble Architecture (Experimental)

This document tracks the Whisper Flow-like floating widget subsystem added under `app/src/main/java/com/wispr/client/overlay`.

## Goal

Show a floating transcription bubble when a text input gains focus in any app, then:
1. record speech,
2. send audio to Whisper Server (`/inference`),
3. insert text into focused field (or copy to clipboard fallback).

This is intentionally decoupled from the existing launcher screen recording flow.

## Permissions And Access Requirements

### Declared In Manifest

1. `android.permission.SYSTEM_ALERT_WINDOW`
2. `android.permission.RECORD_AUDIO`
3. `android.permission.FOREGROUND_SERVICE`
4. `android.permission.FOREGROUND_SERVICE_MICROPHONE`
5. `android.permission.POST_NOTIFICATIONS`

### User-Granted / Special Access (Manual)

1. Overlay permission (`ACTION_MANAGE_OVERLAY_PERMISSION`)
2. Accessibility service enable (`ACTION_ACCESSIBILITY_SETTINGS`) for `WhisperFocusAccessibilityService`
3. Microphone runtime permission
4. Notifications permission (Android 13+ recommended for foreground service notification visibility)

## Components

1. `WhisperFocusAccessibilityService`
- Watches focus/content accessibility events.
- Detects editable focus targets.
- Starts/stops bubble service based on focus.
- Provides focused-field text insertion API.

2. `WhisperFloatingBubbleService`
- Foreground service hosting `TYPE_APPLICATION_OVERLAY` floating UI.
- Drag-and-snap bubble position with persisted coordinates.
- Idle state with single `Mic` action.
- Post-transcription copy quick action shown next to `Mic` for a short timeout.
- Recording state with animated waveform plus:
  - `✓` submit (stop + transcribe)
  - `✕` cancel (discard audio)
- Calls `WhisperServerClient.transcribeAudio(...)`.
- Inserts via accessibility service or clipboard fallback.

3. Helpers
- `OverlayPermission`
- `AccessibilityPermission`
- `AccessibilityServiceState`
- `FocusEventEvaluator`

## Known Gaps (Next Iteration)

1. Better focus filtering across complex webviews/editors.
2. Robust handling for Android 14+ microphone foreground-service edge cases.
3. Better UI polish and expanded quick actions (undo/retry/history).
