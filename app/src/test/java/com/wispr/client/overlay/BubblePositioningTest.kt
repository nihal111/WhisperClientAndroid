package com.wispr.client.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class BubblePositioningTest {
    @Test
    fun `snapToEdge snaps to left when bubble center is on left side`() {
        val snapped = BubblePositioning.snapToEdge(
            rawX = 100,
            rawY = 300,
            bubbleWidth = 200,
            bubbleHeight = 100,
            screenWidth = 1080,
            screenHeight = 1920,
            edgePadding = 24,
        )
        assertEquals(24, snapped.x)
        assertEquals(300, snapped.y)
    }

    @Test
    fun `snapToEdge snaps to right when bubble center is on right side`() {
        val snapped = BubblePositioning.snapToEdge(
            rawX = 700,
            rawY = 300,
            bubbleWidth = 200,
            bubbleHeight = 100,
            screenWidth = 1080,
            screenHeight = 1920,
            edgePadding = 24,
        )
        assertEquals(856, snapped.x)
        assertEquals(300, snapped.y)
    }

    @Test
    fun `snapToEdge clamps y within bounds`() {
        val snapped = BubblePositioning.snapToEdge(
            rawX = 700,
            rawY = 5000,
            bubbleWidth = 200,
            bubbleHeight = 100,
            screenWidth = 1080,
            screenHeight = 1920,
            edgePadding = 24,
        )
        assertEquals(856, snapped.x)
        assertEquals(1796, snapped.y)
    }
}
