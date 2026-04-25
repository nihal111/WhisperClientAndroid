package com.wispr.client.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.sin

/**
 * Custom view that draws an animated audio waveform with rounded vertical bars.
 */
class WaveformView(context: Context) : View(context) {

    private val barCount = 24
    private val barWidthDp = 2.5f
    private val barGapDp = 1.8f
    private val minHeightFraction = 0.12f
    private val cornerRadiusDp = 1.5f

    private val density = context.resources.displayMetrics.density
    private val barWidthPx = barWidthDp * density
    private val barGapPx = barGapDp * density
    private val cornerRadiusPx = cornerRadiusDp * density

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4A9EFF.toInt()
        style = Paint.Style.FILL
    }

    private val barRect = RectF()

    // Each bar gets its own phase offset for organic movement
    private val phaseOffsets = FloatArray(barCount) { i ->
        val center = barCount / 2f
        val distFromCenter = kotlin.math.abs(i - center) / center
        // Bars near edges are quieter; center bars are louder
        distFromCenter * 0.6f + (i * 0.4f)
    }

    // Amplitude envelope — center bars are taller
    private val envelope = FloatArray(barCount) { i ->
        val center = barCount / 2f
        val norm = 1f - (kotlin.math.abs(i - center) / center)
        // Smooth bell curve: mix of linear and squared falloff
        0.25f + 0.75f * norm * norm
    }

    private var animationTime = 0f
    private var currentAmplitude = 0f
    private var maxSeenAmplitude = 0.15f // Start with a reasonable floor
    var isAnimating = false
        private set

    fun startAnimation() {
        isAnimating = true
        animationTime = 0f
        currentAmplitude = 0f
        maxSeenAmplitude = 0.15f
        invalidate()
    }

    fun stopAnimation() {
        isAnimating = false
        animationTime = 0f
        currentAmplitude = 0f
        invalidate()
    }

    fun updateAmplitude(amplitude: Float) {
        // Track the peak amplitude with slow decay for auto-gain effect
        if (amplitude > maxSeenAmplitude) {
            maxSeenAmplitude = amplitude
        } else {
            // Slowly decay the peak so we stay sensitive to quiet speech
            maxSeenAmplitude = (maxSeenAmplitude * 0.992f).coerceAtLeast(0.15f)
        }

        // Normalize current amplitude against the peak (auto-gain)
        val normalized = if (maxSeenAmplitude > 0f) {
            (amplitude / maxSeenAmplitude).coerceIn(0f, 1f)
        } else {
            0f
        }

        // Linear smoothing for visual stability
        currentAmplitude = (currentAmplitude * 0.35f) + (normalized * 0.65f)
        
        if (isAnimating) {
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalWidth = (barCount * barWidthPx + (barCount - 1) * barGapPx).toInt()
        val desiredHeight = (28 * density).toInt()
        setMeasuredDimension(
            resolveSize(totalWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height.toFloat()
        val centerY = h / 2f
        val maxBarHeight = h * 0.85f

        for (i in 0 until barCount) {
            val x = i * (barWidthPx + barGapPx)

            val barHeight = if (isAnimating) {
                // Layer multiple sine waves for organic texture
                val wave1 = sin((animationTime * 5.5f + phaseOffsets[i] * 6.2f).toDouble()).toFloat()
                val wave2 = sin((animationTime * 3.2f + phaseOffsets[i] * 4.1f + 1.3f).toDouble()).toFloat()
                val wave3 = sin((animationTime * 7.8f + phaseOffsets[i] * 2.7f + 2.8f).toDouble()).toFloat()
                val texture = (wave1 * 0.5f + wave2 * 0.3f + wave3 * 0.2f + 1f) / 2f // normalize to 0..1
                
                // Combine real-time amplitude with the texture and envelope
                // We use a base minimum height + amplitude-driven growth
                val scale = minHeightFraction + (1f - minHeightFraction) * currentAmplitude * texture
                val finalScale = (scale * envelope[i]).coerceIn(minHeightFraction, 1.0f)
                finalScale * maxBarHeight
            } else {
                minHeightFraction * maxBarHeight
            }

            val top = centerY - barHeight / 2f
            val bottom = centerY + barHeight / 2f
            barRect.set(x, top, x + barWidthPx, bottom)
            canvas.drawRoundRect(barRect, cornerRadiusPx, cornerRadiusPx, paint)
        }

        if (isAnimating) {
            animationTime += 0.015f
            postInvalidateOnAnimation()
        }
    }
}
