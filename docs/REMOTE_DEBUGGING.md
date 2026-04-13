# Remote On-Device Debugging Guide

This guide documents the workflow for diagnosing and fixing tricky bugs that require real device testing, using wireless ADB and real-time log monitoring. This approach was successfully used to identify and fix the floating bubble keyboard visibility issue.

## Overview

Remote on-device debugging involves:
1. Deploying instrumented code to a real device via wireless ADB
2. Streaming logs in real-time while the app runs
3. Analyzing logs to identify root causes
4. Making targeted fixes based on log insights
5. Re-deploying and confirming the fix

## Prerequisites

- Physical device connected via wireless ADB (see `WIRELESS_DEBUGGING_RUNBOOK.md`)
- App instrumented with detailed logging statements
- Real-time log monitoring setup

## Step 1: Add Detailed Logging to Critical Code Paths

When approaching a tricky bug, instrument the relevant code with detailed logs that capture decision-making and state changes.

### Example: Keyboard Detection Issue

The issue was: "Floating bubble shows even without keyboard open"

**Instrumentation added to `WisprFocusAccessibilityService.kt`:**

```kotlin
private fun isInputMethodWindowVisible(): Boolean {
    // Check for IME window in accessibility window list
    val hasImeWindow = windows.any { window ->
        window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
    }

    // Log all visible windows for debugging
    val windowTypes = windows.map {
        when(it.type) {
            AccessibilityWindowInfo.TYPE_APPLICATION -> "APPLICATION"
            AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "INPUT_METHOD"
            AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
            else -> "TYPE_${it.type}"
        }
    }.joinToString(", ")
    Log.d(TAG, "Visible windows: [$windowTypes]")

    if (hasImeWindow) {
        Log.d(TAG, "Keyboard visible (IME window detected)")
    } else {
        Log.d(TAG, "Keyboard NOT visible (no IME window found)")
    }
    return hasImeWindow
}
```

**In the visibility decision:**

```kotlin
val imeVisible = isInputMethodWindowVisible()
val showWithoutKeyboard = overlayConfigStore.getShowBubbleWithoutKeyboard()
val shouldShow = BubbleVisibilityPolicy.shouldShow(
    hasEditableTarget = focusState.hasEditableTarget,
    hasSensitiveTarget = focusState.hasSensitiveTarget,
    imeWindowVisible = imeVisible,
    showWithoutKeyboard = showWithoutKeyboard,
    eventPackageName = focusState.packageName,
    ownPackageName = packageName,
)
Log.d(TAG, "Visibility decision: editable=${focusState.hasEditableTarget} sensitive=${focusState.hasSensitiveTarget} imeVisible=$imeVisible showWithoutKeyboard=$showWithoutKeyboard -> shouldShow=$shouldShow")
```

**Key principle:** Log ALL inputs to decision-making functions, not just the output. This lets you identify which variable is causing unexpected behavior.

## Step 2: Deploy to Physical Device

Build and install via wireless ADB:

```bash
source ./scripts/env-android.sh

# Connect to device
"$ADB_BIN" connect 100.110.240.42:43897

# Build and install
./gradlew :app:installDebug

# Launch app
"$ADB_BIN" -s 100.110.240.42:43897 shell am start -n com.wispr.client.debug/com.wispr.client.MainActivity

# Enable accessibility service (if needed)
"$ADB_BIN" -s 100.110.240.42:43897 shell settings put secure enabled_accessibility_services \
  com.wispr.client.debug/com.wispr.client.overlay.WhisperFocusAccessibilityService
```

## Step 3: Set Up Real-Time Log Streaming

Start monitoring logs in a dedicated terminal while you interact with the app:

```bash
source ./scripts/env-android.sh
"$ADB_BIN" -s 100.110.240.42:43897 logcat -s WhisperA11y
```

Or use the Monitor tool for streaming output that integrates with Claude Code:

```bash
# In a terminal or Claude Code Monitor task
source ./scripts/env-android.sh && "$ADB_BIN" -s 100.110.240.42:43897 logcat -s WhisperA11y
```

### Interpreting Live Logs

Look for patterns and state transitions:

**Good logs show:**
- `Visible windows: [SYSTEM, SYSTEM, SYSTEM, APPLICATION]` → No keyboard
- `Visible windows: [SYSTEM, SYSTEM, SYSTEM, INPUT_METHOD, APPLICATION]` → Keyboard open
- `Visibility decision: ... shouldShow=false` → Correctly hiding bubble
- `Visibility decision: ... shouldShow=true` → Correctly showing bubble

**Suspicious logs indicate:**
- `shouldShow=true` when it should be `false` → Logic error or wrong setting
- `imeVisible=false` but keyboard appears on screen → Detection mechanism broken
- `showWithoutKeyboard=true` when default is `false` → Setting was overridden

## Step 4: Analyze Logs to Identify Root Cause

From the keyboard visibility case:

**Initial hypothesis:** Keyboard detection is broken

**What the logs revealed:**
```
showWithoutKeyboard=true → shouldShow=true  ← PROBLEM!
showWithoutKeyboard=true imeVisible=false → shouldShow=true  ← Explains the symptom
```

**Root cause identified:** The `showWithoutKeyboard` setting was ON, overriding keyboard requirement

This wasn't a detection problem—it was a configuration issue that was invisible in the code.

## Step 5: Fix and Re-Deploy

Once root cause is identified, the fix may involve:

### Option A: Code Fix
Modify logic, re-build, reinstall:
```bash
./gradlew :app:installDebug
```

### Option B: Configuration/State Fix
Directly modify app state via adb:

```bash
# Read current configuration
"$ADB_BIN" -s 100.110.240.42:43897 shell \
  "run-as com.wispr.client.debug cat /data/data/com.wispr.client.debug/shared_prefs/whisper_overlay.xml"

# Write corrected configuration
cat > /tmp/whisper_overlay.xml << 'EOF'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <int name="bubble_y" value="506" />
    <boolean name="show_without_keyboard" value="false" />
    <int name="bubble_x" value="910" />
</map>
EOF

"$ADB_BIN" -s 100.110.240.42:43897 push /tmp/whisper_overlay.xml /data/local/tmp/
"$ADB_BIN" -s 100.110.240.42:43897 shell \
  "run-as com.wispr.client.debug cp /data/local/tmp/whisper_overlay.xml /data/data/com.wispr.client.debug/shared_prefs/whisper_overlay.xml"

# Restart app to reload configuration
"$ADB_BIN" -s 100.110.240.42:43897 shell am force-stop com.wispr.client.debug
"$ADB_BIN" -s 100.110.240.42:43897 shell am start -n com.wispr.client.debug/com.wispr.client.MainActivity
```

## Step 6: Confirm Fix via Logs

Watch the live logs as the fix takes effect:

**Before fix:**
```
showWithoutKeyboard=true → shouldShow=true  (wrong!)
```

**After fix:**
```
showWithoutKeyboard=false imeVisible=false → shouldShow=false  ✓
showWithoutKeyboard=false imeVisible=true → shouldShow=true   ✓
```

## Best Practices for On-Device Debugging

### 1. **Log All Decision Inputs**
Don't just log the result; log every variable that affects the decision:
```kotlin
// Good ❌
Log.d(TAG, "shouldShow=$shouldShow")

// Better ✅
Log.d(TAG, "editable=$editable imeVisible=$imeVisible keyboard=$showKeyboard -> shouldShow=$shouldShow")
```

### 2. **Use Consistent Log Tags**
Make filtering easy:
```kotlin
private const val TAG = "WhisperA11y"  // Consistent tag for related logs
Log.d(TAG, "message")
```

Filter reliably:
```bash
"$ADB_BIN" logcat -s WhisperA11y
```

### 3. **Log State Transitions**
Show what changed:
```kotlin
Log.d(TAG, "Keyboard NOT visible (no IME window found)")  // More descriptive
Log.d(TAG, "imeVisible=false")  // Less helpful
```

### 4. **Use Timestamps to Correlate Events**
The logcat timestamp helps correlate with device actions:
```
04-12 19:23:42.302  Keyboard visible (IME window detected)  ← tap happened ~here
04-12 19:23:43.739  Keyboard NOT visible (no IME window)    ← keyboard closed ~here
```

### 5. **Monitor While Reproducing the Issue**
Have logs running BEFORE you interact with the app. This captures the exact sequence of events:
```bash
# Start logs first
"$ADB_BIN" logcat -s WhisperA11y &

# Then interact with app
adb shell input tap 540 150  # Focus text field
adb shell input text "hello" # Type something
```

### 6. **Capture Full State at Decision Points**
When a bug manifests, capture the complete state that led to it:
```kotlin
// If showing when it shouldn't:
Log.d(TAG, "State at bubble show: " +
    "hasEditableTarget=$hasEditableTarget " +
    "imeVisible=$imeVisible " +
    "showWithoutKeyboard=$showWithoutKeyboard " +
    "policy.shouldShow=$shouldShow")
```

## Troubleshooting Remote Debugging

### Issue: "No logs appearing"
**Solution:** Verify accessibility service is enabled:
```bash
"$ADB_BIN" shell settings get secure enabled_accessibility_services
```

Should output: `com.wispr.client.debug/com.wispr.client.overlay.WhisperFocusAccessibilityService`

### Issue: "Logs have too much output"
**Solution:** Filter more aggressively:
```bash
# Instead of
"$ADB_BIN" logcat -s WhisperA11y

# Use
"$ADB_BIN" logcat -s WhisperA11y | grep "Visibility decision"
```

### Issue: "Logs stopped streaming"
**Solution:** The monitor may have hit the output limit. Restart with filtering:
```bash
"$ADB_BIN" logcat -s WhisperA11y | grep --line-buffered "Visibility decision"
```

### Issue: "Can't access SharedPreferences file"
**Solution:** Use `run-as` to access app-private storage:
```bash
"$ADB_BIN" shell "run-as com.wispr.client.debug cat /data/data/com.wispr.client.debug/shared_prefs/prefs.xml"
```

## Case Study: The Keyboard Visibility Bug

**Symptoms reported:** "Bubble appears all the time, even without keyboard"

**Initial assumption:** Keyboard detection is broken

**Logging strategy:**
- Log all windows being reported
- Log whether IME window is detected
- Log the complete visibility decision with all variables

**What logs revealed:**
```
Visible windows: [SYSTEM, SYSTEM, SYSTEM, APPLICATION]
Keyboard NOT visible (no IME window found)
Visibility decision: editable=true sensitive=false imeVisible=false showWithoutKeyboard=true → shouldShow=true
```

The detection was working perfectly! The bug was `showWithoutKeyboard=true` overriding the keyboard requirement.

**Fix:** Change setting from true → false

**Confirmation:** After fix, logs showed:
```
imeVisible=false showWithoutKeyboard=false → shouldShow=false  ✓
imeVisible=true showWithoutKeyboard=false → shouldShow=true   ✓
```

## Key Takeaway

When remote debugging, **the logs are your conversation with the device**. Make them speak clearly by:
1. Logging all decision inputs
2. Using consistent tags for filtering
3. Making state transitions obvious
4. Capturing complete context at critical points

This transforms "the bug is somewhere in this code" into "the bug is specifically in this variable because the log shows it has an unexpected value."
