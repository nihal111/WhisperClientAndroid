package com.wispr.client.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.wispr.client.data.StatsSnapshot
import com.wispr.client.data.computeStats
import com.wispr.client.data.db.AppDatabase
import com.wispr.client.data.db.SessionEntity
import com.wispr.client.ui.theme.AccentTeal
import com.wispr.client.ui.theme.BrandBlue
import com.wispr.client.ui.theme.GreenOk
import com.wispr.client.ui.theme.RedError
import com.wispr.client.ui.theme.SurfaceVariant
import com.wispr.client.ui.theme.TextTertiary
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

sealed class TranscriptListItem {
    data class Header(val label: String) : TranscriptListItem()
    data class Entry(val session: SessionEntity) : TranscriptListItem()
}

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

    val listItems = remember(sessions) {
        buildTranscriptItems(sessions)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HeroStatsCard(stats) }
        item { TodayChips(stats) }

        if (sessions.isEmpty()) {
            item { EmptyState() }
        } else {
            items(listItems, key = { item ->
                when (item) {
                    is TranscriptListItem.Header -> "header_${item.label}"
                    is TranscriptListItem.Entry -> "entry_${item.session.id}"
                }
            }) { item ->
                when (item) {
                    is TranscriptListItem.Header -> TranscriptHeader(item.label)
                    is TranscriptListItem.Entry -> {
                        TranscriptCard(item.session, onCopy = {
                            clipboard.setText(AnnotatedString(item.session.transcript))
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStatsCard(stats: StatsSnapshot?) {
    val totalWords = (stats?.totalWords ?: 0L).toInt()
    val wpm = (stats?.lifetimeWpm ?: 0.0).toInt()
    val streak = stats?.streakDays ?: 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        BrandBlue.copy(alpha = 0.12f),
                        SurfaceVariant,
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f, 1000f),
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = totalWords.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = BrandBlue,
            )
            Text(
                text = "WORDS SPOKEN",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) { StatChip(value = wpm, label = "WPM", icon = "⚡") }
                Box(modifier = Modifier.weight(1f)) { StatChip(value = streak, label = "Streak", icon = "🔥") }
            }
        }
    }
}

@Composable
private fun StatChip(value: Int, label: String, icon: String) {
    SuggestionChip(
        onClick = {},
        label = {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(icon, modifier = Modifier.padding(end = 4.dp))
                Text("$value $label")
            }
        },
    )
}

@Composable
private fun TodayChips(stats: StatsSnapshot?) {
    val wordsToday = (stats?.wordsToday ?: 0L).toInt()
    val sessionsToday = (stats?.sessionsToday ?: 0L).toInt()
    val savedMinutes = (stats?.timeSavedTodayMs ?: 0L) / 60_000

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth(0.33f)) { TodayChip("$wordsToday words") }
        Box(modifier = Modifier.fillMaxWidth(0.5f)) { TodayChip("$sessionsToday sessions") }
        TodayChip("${savedMinutes}m saved")
    }
}

@Composable
private fun TodayChip(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(SurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = BrandBlue.copy(alpha = 0.4f),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No recordings yet",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Open any app, tap a text field,\nthen tap the bubble to start.",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary,
        )
    }
}

@Composable
private fun TranscriptHeader(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TranscriptCard(session: SessionEntity, onCopy: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val appIcon = remember(session.sourceApp) {
        session.sourceApp?.let { pkg ->
            try {
                context.packageManager.getApplicationIcon(pkg).toBitmap().asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                appIcon?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it,
                        contentDescription = session.sourceApp,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop,
                    )
                }
                Text(
                    text = formatRelative(session.ts),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text("${session.wordCount}w", fontSize = 9.sp) },
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                }
            }
            Text(
                session.transcript,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (!expanded && session.transcript.lines().size > 3) {
                TextButton(
                    onClick = { expanded = true },
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text("…more")
                }
            } else if (expanded && session.transcript.lines().size > 3) {
                TextButton(
                    onClick = { expanded = false },
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text("less")
                }
            }
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

private fun buildTranscriptItems(sessions: List<SessionEntity>): List<TranscriptListItem> {
    val items = mutableListOf<TranscriptListItem>()
    var lastDayLabel = ""
    for (session in sessions) {
        val dayLabel = getDayLabel(session.ts)
        if (dayLabel != lastDayLabel) {
            items.add(TranscriptListItem.Header(dayLabel))
            lastDayLabel = dayLabel
        }
        items.add(TranscriptListItem.Entry(session))
    }
    return items
}

private fun getDayLabel(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    val oneDayMs = 86_400_000L

    return when {
        diff < oneDayMs -> "Today"
        diff < 2 * oneDayMs -> "Yesterday"
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(ts))
    }
}
