package com.wispr.client.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleVisibilityPolicyTest {
    @Test
    fun `shows bubble when editable and keyboard visible`() {
        assertTrue(
            BubbleVisibilityPolicy.shouldShow(
                hasEditableTarget = true,
                hasSensitiveTarget = false,
                imeWindowVisible = true,
                showWithoutKeyboard = false,
                eventPackageName = "com.example.notes",
                ownPackageName = "com.wispr.client",
            ),
        )
    }

    @Test
    fun `hides bubble on sensitive fields`() {
        assertFalse(
            BubbleVisibilityPolicy.shouldShow(
                hasEditableTarget = true,
                hasSensitiveTarget = true,
                imeWindowVisible = true,
                showWithoutKeyboard = true,
                eventPackageName = "com.example.notes",
                ownPackageName = "com.wispr.client",
            ),
        )
    }

    @Test
    fun `hides bubble for blocked app package`() {
        assertFalse(
            BubbleVisibilityPolicy.shouldShow(
                hasEditableTarget = true,
                hasSensitiveTarget = false,
                imeWindowVisible = true,
                showWithoutKeyboard = true,
                eventPackageName = "com.superbank.mobile",
                ownPackageName = "com.wispr.client",
            ),
        )
    }

    @Test
    fun `hides bubble when keyboard hidden and override disabled`() {
        assertFalse(
            BubbleVisibilityPolicy.shouldShow(
                hasEditableTarget = true,
                hasSensitiveTarget = false,
                imeWindowVisible = false,
                showWithoutKeyboard = false,
                eventPackageName = "com.example.notes",
                ownPackageName = "com.wispr.client",
            ),
        )
    }

    @Test
    fun `hides bubble when current focus is non-editable even if keyboard is visible`() {
        assertFalse(
            BubbleVisibilityPolicy.shouldShow(
                hasEditableTarget = false,
                hasSensitiveTarget = false,
                imeWindowVisible = true,
                showWithoutKeyboard = false,
                eventPackageName = "com.example.notes",
                ownPackageName = "com.wispr.client",
            ),
        )
    }

    @Test
    fun `hides bubble on non-editable events when keyboard is not visible`() {
        assertFalse(
            BubbleVisibilityPolicy.shouldShow(
                hasEditableTarget = false,
                hasSensitiveTarget = false,
                imeWindowVisible = false,
                showWithoutKeyboard = true,
                eventPackageName = "com.example.notes",
                ownPackageName = "com.wispr.client",
            ),
        )
    }
}
