package com.wispr.client.overlay

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

object FocusEventEvaluator {
    fun hasEditableTarget(
        event: AccessibilityEvent?,
        appPackageName: String,
    ): Boolean? {
        if (event == null) {
            return null
        }
        if (!isRelevantEvent(event.eventType)) {
            return null
        }
        val source = event.source ?: return null
        return try {
            val eventPackage = event.packageName?.toString().orEmpty()
            if (eventPackage == appPackageName) {
                return false
            }
            source.hasEditableDescendant()
        } finally {
            source.recycle()
        }
    }

    private fun AccessibilityNodeInfo.hasEditableDescendant(): Boolean {
        if (isProbablyEditable(this)) {
            return true
        }
        for (index in 0 until childCount) {
            val child = getChild(index) ?: continue
            try {
                if (child.hasEditableDescendant()) {
                    return true
                }
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun isProbablyEditable(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) {
            return true
        }
        val className = node.className?.toString().orEmpty()
        return className.contains("EditText", ignoreCase = true) ||
            className.contains("TextInput", ignoreCase = true)
    }

    private fun isRelevantEvent(type: Int): Boolean {
        return type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    }
}
