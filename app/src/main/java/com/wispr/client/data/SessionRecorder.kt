package com.wispr.client.data

import android.content.Context
import com.wispr.client.data.db.AppDatabase
import com.wispr.client.data.db.SessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SessionRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun record(
        context: Context,
        transcript: String,
        durationMs: Long,
        sourceApp: String?,
    ) {
        val cleaned = transcript.trim()
        if (cleaned.isEmpty()) return
        val wordCount = cleaned.split(Regex("\\s+")).count { it.isNotBlank() }
        val entity = SessionEntity(
            ts = System.currentTimeMillis(),
            wordCount = wordCount,
            durationMs = durationMs.coerceAtLeast(0L),
            sourceApp = sourceApp,
            transcript = cleaned,
        )
        val dao = AppDatabase.get(context).sessions()
        scope.launch { dao.insert(entity) }
        TranscriptStore(context).setLastTranscript(cleaned)
    }
}
