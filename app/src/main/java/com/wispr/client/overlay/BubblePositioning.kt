package com.wispr.client.overlay

data class BubblePosition(val x: Int, val y: Int)

object BubblePositioning {
    fun snapToEdge(
        rawX: Int,
        rawY: Int,
        bubbleWidth: Int,
        bubbleHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
        edgePadding: Int,
    ): BubblePosition {
        val leftX = edgePadding
        val rightX = (screenWidth - bubbleWidth - edgePadding).coerceAtLeast(edgePadding)
        val midpoint = screenWidth / 2
        val snappedX = if (rawX + (bubbleWidth / 2) >= midpoint) rightX else leftX

        val minY = edgePadding
        val maxY = (screenHeight - bubbleHeight - edgePadding).coerceAtLeast(minY)
        val snappedY = rawY.coerceIn(minY, maxY)
        return BubblePosition(snappedX, snappedY)
    }
}
