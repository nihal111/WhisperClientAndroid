package com.wispr.client.overlay

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

class WisprFocusAccessibilityService : AccessibilityService() {
    private var lastEditableState: Boolean? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val editable = FocusEventEvaluator.hasEditableTarget(event, packageName) ?: return
        if (editable == lastEditableState) {
            return
        }
        lastEditableState = editable
        val action = if (editable) {
            WisprFloatingBubbleService.ACTION_FOCUS_EDITABLE
        } else {
            WisprFloatingBubbleService.ACTION_FOCUS_NON_EDITABLE
        }
        WisprFloatingBubbleService.sendCommand(this, action)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
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
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val didSet = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (didSet) {
            return true
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    companion object {
        private const val TAG = "WisprA11y"
        private var instanceRef: WeakReference<WisprFocusAccessibilityService>? = null

        fun getInstance(): WisprFocusAccessibilityService? = instanceRef?.get()
    }
}
