package com.wispr.client.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val wordCount: Int,
    val durationMs: Long,
    val sourceApp: String?,
    val transcript: String,
)
