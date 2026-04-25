package com.wispr.client.data

import com.wispr.client.data.db.SessionDao
import java.util.Calendar
import java.util.TimeZone

data class StatsSnapshot(
    val totalWords: Long,
    val totalSessions: Long,
    val totalDurationMs: Long,
    val lifetimeWpm: Double,
    val streakDays: Int,
    val wordsToday: Long,
    val sessionsToday: Long,
    val durationTodayMs: Long,
    val timeSavedTodayMs: Long,
)

private const val ASSUMED_TYPING_WPM = 40.0

suspend fun computeStats(dao: SessionDao): StatsSnapshot {
    val totalWords = dao.totalWords()
    val totalDurationMs = dao.totalDurationMs()
    val totalSessions = dao.totalSessions()

    val lifetimeWpm = if (totalDurationMs > 0)
        totalWords.toDouble() / (totalDurationMs / 60000.0)
    else 0.0

    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0);     cal.set(Calendar.MILLISECOND, 0)
    val startOfTodayMs = cal.timeInMillis
    val endOfTodayMs = startOfTodayMs + 86_400_000L

    val wordsToday = dao.wordsBetween(startOfTodayMs, endOfTodayMs)
    val sessionsToday = dao.sessionsBetween(startOfTodayMs, endOfTodayMs)
    val durationTodayMs = dao.durationBetween(startOfTodayMs, endOfTodayMs)

    val typingMsForTodaysWords = (wordsToday / ASSUMED_TYPING_WPM * 60_000.0).toLong()
    val timeSavedTodayMs = (typingMsForTodaysWords - durationTodayMs).coerceAtLeast(0L)

    val streakDays = computeStreak(dao.distinctDayBucketsDesc())

    return StatsSnapshot(
        totalWords = totalWords,
        totalSessions = totalSessions,
        totalDurationMs = totalDurationMs,
        lifetimeWpm = lifetimeWpm,
        streakDays = streakDays,
        wordsToday = wordsToday,
        sessionsToday = sessionsToday,
        durationTodayMs = durationTodayMs,
        timeSavedTodayMs = timeSavedTodayMs,
    )
}

private fun computeStreak(utcDayBucketsDesc: List<Long>): Int {
    if (utcDayBucketsDesc.isEmpty()) return 0
    val tzOffsetMs = TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
    val localDays = utcDayBucketsDesc
        .map { (it * 86_400_000L + tzOffsetMs) / 86_400_000L }
        .distinct()
        .sortedDescending()
    val today = (System.currentTimeMillis() + tzOffsetMs) / 86_400_000L
    val mostRecent = localDays.first()
    if (mostRecent != today && mostRecent != today - 1) return 0
    var streak = 1
    var expected = mostRecent - 1
    for (day in localDays.drop(1)) {
        if (day == expected) { streak++; expected-- } else break
    }
    return streak
}
