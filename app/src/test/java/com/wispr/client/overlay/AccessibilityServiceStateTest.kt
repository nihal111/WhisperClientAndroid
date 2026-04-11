package com.wispr.client.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceStateTest {
    @Test
    fun `isServiceEnabledInSetting returns true for matching id`() {
        val enabled = "a/b:c/d:com.wispr.client/com.wispr.client.overlay.WhisperFocusAccessibilityService"
        val target = "com.wispr.client/com.wispr.client.overlay.WhisperFocusAccessibilityService"
        assertTrue(AccessibilityServiceState.isServiceEnabledInSetting(enabled, target))
    }

    @Test
    fun `isServiceEnabledInSetting returns false for missing id`() {
        val enabled = "a/b:c/d"
        val target = "com.wispr.client/com.wispr.client.overlay.WhisperFocusAccessibilityService"
        assertFalse(AccessibilityServiceState.isServiceEnabledInSetting(enabled, target))
    }

    @Test
    fun `isServiceEnabledInSetting returns false for blank setting`() {
        val target = "com.wispr.client/com.wispr.client.overlay.WhisperFocusAccessibilityService"
        assertFalse(AccessibilityServiceState.isServiceEnabledInSetting("", target))
    }
}
