package com.wispr.client.overlay

object AccessibilityServiceState {
    internal fun isServiceEnabledInSetting(
        enabledServicesSetting: String?,
        serviceId: String,
    ): Boolean {
        if (enabledServicesSetting.isNullOrBlank()) {
            return false
        }
        return enabledServicesSetting
            .split(':')
            .map { it.trim() }
            .any { it.equals(serviceId, ignoreCase = true) }
    }
}
