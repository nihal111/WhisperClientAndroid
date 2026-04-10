# Flow Bubble Architecture (Experimental)

This document tracks the Wispr Flow-like floating widget subsystem added under `app/src/main/java/com/wispr/client/overlay`.

## Goal

Show a floating transcription bubble when a text input gains focus in any app, then:
1. record speech,
2. send audio to Wispr Server (`/inference`),
3. insert text into focused field (or copy to clipboard fallback).

This is intentionally decoupled from the existing launcher screen recording flow and IME flow.

## Permissions And Access Requirements

### Declared In Manifest

1. `android.permission.SYSTEM_ALERT_WINDOW`
2. `android.permission.RECORD_AUDIO`
3. `android.permission.FOREGROUND_SERVICE`
4. `android.permission.FOREGROUND_SERVICE_MICROPHONE`
5. `android.permission.POST_NOTIFICATIONS`

### User-Granted / Special Access (Manual)

1. Overlay permission (`ACTION_MANAGE_OVERLAY_PERMISSION`)
2. Accessibility service enable (`ACTION_ACCESSIBILITY_SETTINGS`) for `WisprFocusAccessibilityService`
3. Microphone runtime permission
4. Notifications permission (Android 13+ recommended for foreground service notification visibility)

## Components

1. `WisprFocusAccessibilityService`
- Watches focus/content accessibility events.
- Detects editable focus targets.
- Starts/stops bubble service based on focus.
- Provides focused-field text insertion API.

2. `WisprFloatingBubbleService`
- Foreground service hosting `TYPE_APPLICATION_OVERLAY` floating UI.
- Record/stop buttons for quick dictation.
- Calls `WisprServerClient.transcribeAudio(...)`.
- Inserts via accessibility service or clipboard fallback.

3. Helpers
- `OverlayPermission`
- `AccessibilityPermission`
- `AccessibilityServiceState`
- `FocusEventEvaluator`

## Known Gaps (Next Iteration)

1. Drag/reposition and dock behavior for bubble.
2. Better focus filtering across complex webviews/editors.
3. Robust handling for Android 14+ microphone foreground-service edge cases.
4. Better UI polish and expanded quick actions (undo/retry/history).
