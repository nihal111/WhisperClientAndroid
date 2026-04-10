package com.wispr.client.overlay

import android.content.Context

class OverlayConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getShowBubbleWithoutKeyboard(): Boolean {
        return prefs.getBoolean(KEY_SHOW_WITHOUT_KEYBOARD, true)
    }

    fun setShowBubbleWithoutKeyboard(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WITHOUT_KEYBOARD, value).apply()
    }

    fun getBubbleX(): Int? {
        return if (prefs.contains(KEY_BUBBLE_X)) prefs.getInt(KEY_BUBBLE_X, 0) else null
    }

    fun getBubbleY(): Int? {
        return if (prefs.contains(KEY_BUBBLE_Y)) prefs.getInt(KEY_BUBBLE_Y, 0) else null
    }

    fun setBubblePosition(x: Int, y: Int) {
        prefs.edit()
            .putInt(KEY_BUBBLE_X, x)
            .putInt(KEY_BUBBLE_Y, y)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "whisper_overlay"
        private const val KEY_SHOW_WITHOUT_KEYBOARD = "show_without_keyboard"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
    }
}
