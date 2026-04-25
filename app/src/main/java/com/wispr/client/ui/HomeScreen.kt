package com.wispr.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wispr.client.data.StatsSnapshot
import com.wispr.client.data.computeStats
import com.wispr.client.data.db.AppDatabase
import com.wispr.client.data.db.SessionEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val dao = remember { AppDatabase.get(context).sessions() }
    val sessions by dao.recent(20).collectAsState(initial = emptyList())
    var stats by remember { mutableStateOf<StatsSnapshot?>(null) }

    LaunchedEffect(sessions.size) {
        stats = computeStats(dao)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HeroStatsCard(stats) }
        item { TodayRow(stats) }
        item { Text("Recent transcripts", style = MaterialTheme.typography.titleMedium) }
        if (sessions.isEmpty()) {
            item { Text("No transcripts yet — tap the bubble in any app to start.") }
        } else {
            items(sessions, key = { it.id }) { session ->
                TranscriptCard(session, onCopy = {
                    clipboard.setText(AnnotatedString(session.transcript))
                })
            }
        }
    }
}

@Composable
private fun HeroStatsCard(stats: StatsSnapshot?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatBlock(value = (stats?.totalWords ?: 0L).toString(), label = "Words")
            StatBlock(value = "%.0f".format(stats?.lifetimeWpm ?: 0.0), label = "WPM")
            StatBlock(value = (stats?.streakDays ?: 0).toString(), label = "Streak")
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TodayRow(stats: StatsSnapshot?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatBlock(value = (stats?.wordsToday ?: 0L).toString(), label = "Words today")
        StatBlock(value = (stats?.sessionsToday ?: 0L).toString(), label = "Sessions")
        StatBlock(
            value = formatMinutes(stats?.timeSavedTodayMs ?: 0L),
            label = "Saved",
        )
    }
}

private fun formatMinutes(ms: Long): String {
    val mins = ms / 60_000
    return if (mins < 60) "${mins}m" else "%dh%dm".format(mins / 60, mins % 60)
}

@Composable
private fun TranscriptCard(session: SessionEntity, onCopy: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${formatRelative(session.ts)} · ${session.wordCount} words" +
                        (session.sourceApp?.let { " · ${shortPkg(it)}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCopy) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                }
            }
            Text(
                session.transcript,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
            )
        }
    }
}

private fun formatRelative(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ts))
    }
}

private fun shortPkg(pkg: String): String = pkg.substringAfterLast('.')
