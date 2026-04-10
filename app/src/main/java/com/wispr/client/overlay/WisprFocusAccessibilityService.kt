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

class WisprFocusAccessibilityService : AccessibilityService() {
    private var lastEditableState: Boolean? = null
    private lateinit var overlayConfigStore: OverlayConfigStore
    private val mainHandler = Handler(Looper.getMainLooper())
    private val delayedHide = Runnable { setBubbleVisible(false) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlayConfigStore = OverlayConfigStore(this)
        instanceRef = WeakReference(this)
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val focusState = FocusEventEvaluator.evaluate(event, packageName) ?: return
        val shouldShow = BubbleVisibilityPolicy.shouldShow(
            hasEditableTarget = focusState.hasEditableTarget,
            hasSensitiveTarget = focusState.hasSensitiveTarget,
            imeWindowVisible = isInputMethodWindowVisible(),
            showWithoutKeyboard = overlayConfigStore.getShowBubbleWithoutKeyboard(),
            eventPackageName = focusState.packageName,
            ownPackageName = packageName,
        )
        if (shouldShow) {
            mainHandler.removeCallbacks(delayedHide)
            setBubbleVisible(true)
        } else if (lastEditableState == true) {
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
            WisprFloatingBubbleService.ACTION_FOCUS_EDITABLE
        } else {
            WisprFloatingBubbleService.ACTION_FOCUS_NON_EDITABLE
        }
        WisprFloatingBubbleService.sendCommand(this, action)
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
        return windows.any { window ->
            window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        }
    }

    companion object {
        private const val TAG = "WisprA11y"
        private const val HIDE_DEBOUNCE_MS = 450L
        private var instanceRef: WeakReference<WisprFocusAccessibilityService>? = null

        fun getInstance(): WisprFocusAccessibilityService? = instanceRef?.get()
    }
}
