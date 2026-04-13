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
                return null
            }
            val state = source.inspectTarget()
            FocusTargetState(
                hasEditableTarget = state.hasEditable,
                hasSensitiveTarget = state.hasSensitive,
                packageName = eventPackage,
            )
        } finally {
            source.recycle()
        }
    }

    fun evaluateFromRoot(
        root: AccessibilityNodeInfo?,
        eventPackageName: String?,
        appPackageName: String,
    ): FocusTargetState? {
        if (root == null) {
            return null
        }
        return try {
            val eventPackage = eventPackageName.orEmpty()
            if (eventPackage == appPackageName) {
                return null
            }
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused == null) {
                return null
            }
            val state = try {
                inspectNode(focused)
            } finally {
                focused.recycle()
            }
            FocusTargetState(
                hasEditableTarget = state.hasEditable,
                hasSensitiveTarget = state.hasSensitive,
                packageName = eventPackage,
            )
        } finally {
            root.recycle()
        }
    }

    private data class NodeScan(
        val hasEditable: Boolean,
        val hasSensitive: Boolean,
    )

    private fun AccessibilityNodeInfo.inspectTarget(): NodeScan {
        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            try {
                return inspectNode(focused)
            } finally {
                focused.recycle()
            }
        }
        return inspectNode(this)
    }

    private fun inspectNode(node: AccessibilityNodeInfo): NodeScan {
        val editable = isProbablyEditable(node)
        if (!editable) {
            android.util.Log.d(
                "FocusEventEvaluator",
                "Node not editable: class=${node.className} isEditable=${node.isEditable} inputType=${node.inputType} hint=${node.hintText} contentDesc=${node.contentDescription}"
            )
            return NodeScan(hasEditable = false, hasSensitive = false)
        }
        return NodeScan(
            hasEditable = true,
            hasSensitive = isSensitiveField(node),
        )
    }

    private fun isProbablyEditable(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) {
            return true
        }
        val className = node.className?.toString().orEmpty()
        if (className.contains("EditText", ignoreCase = true) ||
            className.contains("TextInput", ignoreCase = true)
        ) {
            return true
        }
        // Check if node has text input action (handles Compose and other frameworks)
        if (node.inputType != 0) {
            return true
        }
        // Check parent nodes for Compose TextField (ComposeView hierarchy)
        // If the focused node has a hint or content desc, it might be a text field
        val hasHint = !node.hintText.isNullOrEmpty()
        val hasContentDesc = !node.contentDescription.isNullOrEmpty()
        if (hasHint || hasContentDesc) {
            return true
        }
        return false
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
