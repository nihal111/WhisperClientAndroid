package com.wispr.client.overlay

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Handler
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.lang.ref.WeakReference

class WhisperFocusAccessibilityService : AccessibilityService() {
    private var lastEditableState: Boolean? = null
    private var bubbleServicePrimed = false
    private lateinit var overlayConfigStore: OverlayConfigStore
    private val mainHandler = Handler(Looper.getMainLooper())
    private val delayedHide = Runnable { setBubbleVisible(false) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayConfigStore = OverlayConfigStore(this)
        instanceRef = WeakReference(this)
        WhisperFloatingBubbleService.sendCommand(this, WhisperFloatingBubbleService.ACTION_START)
        bubbleServicePrimed = true
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!bubbleServicePrimed) {
            WhisperFloatingBubbleService.sendCommand(this, WhisperFloatingBubbleService.ACTION_START)
            bubbleServicePrimed = true
        }
        var focusState = FocusEventEvaluator.evaluate(event, packageName)

        // When the event source says no editable target, cross-check against
        // the full window root. Content-change events often fire from
        // non-editable nodes (toolbar, labels) even while an editable field
        // retains input focus.
        if (focusState != null && !focusState.hasEditableTarget) {
            val rootState = FocusEventEvaluator.evaluateFromRoot(
                rootInActiveWindow, event?.packageName?.toString(), packageName,
            )
            if (rootState != null && rootState.hasEditableTarget) {
                focusState = rootState
            }
        }

        if (focusState == null) {
            focusState = FocusEventEvaluator.evaluateFromRoot(
                rootInActiveWindow, event?.packageName?.toString(), packageName,
            )
        }

        if (focusState == null) return

        if (focusState.hasEditableTarget) {
            focusState.packageName?.let { lastFocusedAppPackage = it }
        }

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
        if (shouldShow) {
            mainHandler.removeCallbacks(delayedHide)
            setBubbleVisible(true)
        } else if (lastEditableState == true) {
            // Only focus and window-state events are reliable hide signals.
            // TYPE_WINDOW_CONTENT_CHANGED and TYPE_VIEW_CLICKED fire
            // constantly (every keystroke, every UI update) and cause
            // false hides while the text field still has focus.
            val eventType = event?.eventType ?: return
            if (eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED &&
                eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            ) {
                return
            }
            mainHandler.removeCallbacks(delayedHide)
            mainHandler.postDelayed(delayedHide, HIDE_DEBOUNCE_MS)
        } else {
            setBubbleVisible(false)
        }
    }

    private fun setBubbleVisible(visible: Boolean) {
        if (visible == lastEditableState) {
            return
        }
        lastEditableState = visible
        val action = if (visible) {
            WhisperFloatingBubbleService.ACTION_FOCUS_EDITABLE
        } else {
            WhisperFloatingBubbleService.ACTION_FOCUS_NON_EDITABLE
        }
        WhisperFloatingBubbleService.sendCommand(this, action)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        mainHandler.removeCallbacks(delayedHide)
        if (instanceRef?.get() === this) {
            instanceRef = null
        }
        super.onDestroy()
    }

    fun insertTextIntoFocusedField(text: String): Boolean {
        val target = findFocusedNode() ?: return false
        return try {
            setTextOnNode(target, text)
        } finally {
            target.recycle()
        }
    }

    private fun findFocusedNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        try {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                return focused
            }
            return findEditableNode(root)
        } finally {
            root.recycle()
        }
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                val found = findEditableNode(child)
                if (found != null) {
                    return found
                }
            } finally {
                child.recycle()
            }
        }
        return null
    }

    private fun setTextOnNode(
        node: AccessibilityNodeInfo,
        text: String,
    ): Boolean {
        val normalized = text.trim()
        if (normalized.isBlank()) {
            return false
        }

        val existing = node.text?.toString().orEmpty()
        val selectionStart = node.textSelectionStart
        val selectionEnd = node.textSelectionEnd

        val didSet = if (existing.isNotEmpty() && selectionStart >= 0 && selectionEnd >= 0) {
            val safeStart = minOf(selectionStart, selectionEnd).coerceIn(0, existing.length)
            val safeEnd = maxOf(selectionStart, selectionEnd).coerceIn(0, existing.length)
            val mergedText = buildString {
                append(existing.substring(0, safeStart))
                append(normalized)
                append(existing.substring(safeEnd))
            }
            performSetText(node, mergedText)
        } else {
            performSetText(node, normalized)
        }

        if (didSet) {
            return true
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("WhisperClient", normalized))
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun performSetText(node: AccessibilityNodeInfo, value: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

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

    companion object {
        private const val TAG = "WhisperA11y"
        private const val HIDE_DEBOUNCE_MS = 450L
        private var instanceRef: WeakReference<WhisperFocusAccessibilityService>? = null
        @Volatile var lastFocusedAppPackage: String? = null

        fun getInstance(): WhisperFocusAccessibilityService? = instanceRef?.get()
    }
}
