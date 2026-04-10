package com.wispr.client.data

import android.content.Context

class TranscriptStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastTranscript(): String {
        return prefs.getString(KEY_LAST_TRANSCRIPT, "").orEmpty()
    }

    fun setLastTranscript(text: String) {
        prefs.edit().putString(KEY_LAST_TRANSCRIPT, text).apply()
    }

    companion object {
        private const val PREFS_NAME = "whisper_client"
        private const val KEY_LAST_TRANSCRIPT = "last_transcript"
    }
}
