package com.wispr.client.overlay

object BubbleVisibilityPolicy {
    private val blockedPackageHints = listOf(
        "wallet",
        "pay",
        "bank",
        "finance",
        "crypto",
        "upi",
    )

    fun shouldShow(
        hasEditableTarget: Boolean,
        hasSensitiveTarget: Boolean,
        imeWindowVisible: Boolean,
        showWithoutKeyboard: Boolean,
        eventPackageName: String,
        ownPackageName: String,
    ): Boolean {
        if (hasSensitiveTarget) return false
        if (eventPackageName == ownPackageName) return false
        if (isBlockedPackage(eventPackageName)) return false
        return hasEditableTarget && (showWithoutKeyboard || imeWindowVisible)
    }

    internal fun isBlockedPackage(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        return blockedPackageHints.any { hint -> normalized.contains(hint) }
    }
}
