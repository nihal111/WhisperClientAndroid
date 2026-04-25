package com.wispr.client.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions ORDER BY ts DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<SessionEntity>>

    @Query("SELECT COALESCE(SUM(wordCount), 0) FROM sessions")
    suspend fun totalWords(): Long

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM sessions")
    suspend fun totalDurationMs(): Long

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun totalSessions(): Long

    @Query("SELECT COALESCE(SUM(wordCount), 0) FROM sessions WHERE ts >= :startMs AND ts < :endMs")
    suspend fun wordsBetween(startMs: Long, endMs: Long): Long

    @Query("SELECT COUNT(*) FROM sessions WHERE ts >= :startMs AND ts < :endMs")
    suspend fun sessionsBetween(startMs: Long, endMs: Long): Long

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM sessions WHERE ts >= :startMs AND ts < :endMs")
    suspend fun durationBetween(startMs: Long, endMs: Long): Long

    @Query("SELECT DISTINCT (ts / 86400000) AS dayBucket FROM sessions ORDER BY dayBucket DESC")
    suspend fun distinctDayBucketsDesc(): List<Long>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM sessions ORDER BY ts ASC")
    suspend fun all(): List<SessionEntity>

    @Insert
    suspend fun insertAll(sessions: List<SessionEntity>)
}
