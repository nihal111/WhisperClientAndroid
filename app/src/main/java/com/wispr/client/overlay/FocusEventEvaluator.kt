package com.wispr.client.overlay

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class FocusTargetState(
    val hasEditableTarget: Boolean,
    val hasSensitiveTarget: Boolean,
    val packageName: String,
)

object FocusEventEvaluator {
    fun evaluate(
        event: AccessibilityEvent?,
        appPackageName: String,
    ): FocusTargetState? {
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
                return FocusTargetState(
                    hasEditableTarget = false,
                    hasSensitiveTarget = false,
                    packageName = eventPackage,
                )
            }
            val state = source.inspectSubtree()
            FocusTargetState(
                hasEditableTarget = state.hasEditable,
                hasSensitiveTarget = state.hasSensitive,
                packageName = eventPackage,
            )
        } finally {
            source.recycle()
        }
    }

    private data class NodeScan(
        val hasEditable: Boolean,
        val hasSensitive: Boolean,
    )

    private fun AccessibilityNodeInfo.inspectSubtree(): NodeScan {
        var hasEditable = false
        var hasSensitive = false

        if (isProbablyEditable(this)) {
            hasEditable = true
            if (isSensitiveField(this)) {
                hasSensitive = true
            }
        }

        for (index in 0 until childCount) {
            val child = getChild(index) ?: continue
            try {
                val childScan = child.inspectSubtree()
                hasEditable = hasEditable || childScan.hasEditable
                hasSensitive = hasSensitive || childScan.hasSensitive
                if (hasEditable && hasSensitive) {
                    break
                }
            } finally {
                child.recycle()
            }
        }
        return NodeScan(
            hasEditable = hasEditable,
            hasSensitive = hasSensitive,
        )
    }

    private fun isProbablyEditable(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) {
            return true
        }
        val className = node.className?.toString().orEmpty()
        return className.contains("EditText", ignoreCase = true) ||
            className.contains("TextInput", ignoreCase = true)
    }

    private fun isSensitiveField(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) {
            return true
        }
        val hint = node.hintText?.toString()?.lowercase().orEmpty()
        if (
            hint.contains("password") ||
            hint.contains("pin") ||
            hint.contains("card") ||
            hint.contains("cvv") ||
            hint.contains("otp")
        ) {
            return true
        }
        val id = node.viewIdResourceName?.lowercase().orEmpty()
        return id.contains("password") || id.contains("pin") || id.contains("card")
    }

    private fun isRelevantEvent(type: Int): Boolean {
        return type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
    }
}
