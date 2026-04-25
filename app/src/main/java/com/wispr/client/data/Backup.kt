package com.wispr.client.data

import android.content.Context
import android.net.Uri
import com.wispr.client.data.db.AppDatabase
import com.wispr.client.data.db.SessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val BACKUP_VERSION = 1

suspend fun exportToUri(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
    val sessions = AppDatabase.get(context).sessions().all()
    val arr = JSONArray()
    for (s in sessions) {
        arr.put(JSONObject().apply {
            put("ts", s.ts)
            put("wordCount", s.wordCount)
            put("durationMs", s.durationMs)
            put("sourceApp", s.sourceApp ?: JSONObject.NULL)
            put("transcript", s.transcript)
        })
    }
    val root = JSONObject().apply {
        put("version", BACKUP_VERSION)
        put("exportedAt", System.currentTimeMillis())
        put("sessions", arr)
    }
    context.contentResolver.openOutputStream(uri, "wt")?.use {
        it.write(root.toString().toByteArray())
    }
    sessions.size
}

suspend fun importFromUri(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
    val text = context.contentResolver.openInputStream(uri)?.use {
        it.readBytes().toString(Charsets.UTF_8)
    } ?: return@withContext 0
    val root = JSONObject(text)
    val arr = root.getJSONArray("sessions")
    val list = mutableListOf<SessionEntity>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        list.add(
            SessionEntity(
                ts = o.getLong("ts"),
                wordCount = o.getInt("wordCount"),
                durationMs = o.getLong("durationMs"),
                sourceApp = if (o.isNull("sourceApp")) null else o.getString("sourceApp"),
                transcript = o.getString("transcript"),
            )
        )
    }
    AppDatabase.get(context).sessions().insertAll(list)
    list.size
}
