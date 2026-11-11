package com.aura.aura_ui.presentation.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.aura_ui.data.session.SessionOutcome
import com.aura.aura_ui.data.session.SessionSummary
import com.aura.aura_ui.data.session.TraceEvent
import com.aura.aura_ui.data.session.TraceType
import com.aura.aura_ui.presentation.AuraViewModel
import com.aura.aura_ui.presentation.components.SessionRow
import com.aura.aura_ui.presentation.components.outcomeLabel
import com.aura.aura_ui.presentation.components.outcomeTone
import com.aura.aura_ui.presentation.formatBytes
import com.aura.aura_ui.presentation.formatDuration
import com.aura.aura_ui.presentation.navigation.Routes
import com.aura.aura_ui.ui.components.Badge
import com.aura.aura_ui.ui.components.ChipTone
import com.aura.aura_ui.ui.components.EmptyState
import com.aura.aura_ui.ui.components.GhostButton
import com.aura.aura_ui.ui.components.InkButton
import com.aura.aura_ui.ui.components.KeyValueRow
import com.aura.aura_ui.ui.components.SectionHeader
import com.aura.aura_ui.ui.components.SurfaceCard
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyle
import com.aura.aura_ui.ui.theme.MonoStyleSmall

@Composable
fun ActivityScreen(vm: AuraViewModel, onOpenSession: (String) -> Unit, onNavigate: (String) -> Unit) {
    val mono = LocalMonoScheme.current
    val sessions by vm.recentSessions.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshSessions() }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Activity", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Every run, every tool call, every verification. Stored locally.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = mono.textSecondary,
                    )
                }
                GhostButton("Refresh", { vm.refreshSessions() })
            }
            Spacer(Modifier.height(16.dp))
            if (sessions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge("${sessions.size} runs")
                    val success = sessions.count { it.outcome == SessionOutcome.SUCCESS }
                    Badge("$success succeeded", tone = if (success > 0) ChipTone.Live else ChipTone.Idle)
                    Badge("${sessions.sumOf { it.steps }} steps")
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        if (sessions.isEmpty()) {
            item {
                EmptyState(
                    title = "No sessions",
                    detail = "Start a run from Home. The trace is written as it happens, not at the end.",
                )
            }
        } else {
            items(sessions, key = { it.id }) { session ->
                SurfaceCard(modifier = Modifier.padding(vertical = 4.dp), contentPadding = PaddingValues(0.dp)) {
                    SessionRow(
                        session = session,
                        onClick = { onOpenSession(session.id) },
                        onDelete = { vm.deleteSession(session.id) },
                    )
                }
            }
            item {
                Spacer(Modifier.height(18.dp))
                SurfaceCard {
                    Text("Retention", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Traces and screenshots are pruned on the retention window. You can prune now or clear everything.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = mono.textSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("Retention settings", { onNavigate(Routes.ACTIVITY_RETENTION) })
                        GhostButton("Clear all", { confirmClear = true }, color = mono.blood)
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete every session?") },
            text = { Text("This removes all traces and screenshots on this device. It cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearSessions()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SessionDetailScreen(vm: AuraViewModel, sessionId: String) {
    val mono = LocalMonoScheme.current
    val sessions by vm.recentSessions.collectAsStateWithLifecycle()
    val summary = sessions.firstOrNull { it.id == sessionId }
    val events = remember(sessionId) { vm.sessionEvents(sessionId) }
    val b2Message by vm.b2Message.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Session", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(summary?.goal ?: beforeFirst(events) ?: "Run $sessionId", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = mono.textSecondary)
            Spacer(Modifier.height(16.dp))
            if (summary != null) {
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Badge(outcomeLabel(summary.outcome), tone = outcomeTone(summary.outcome))
                        Spacer(Modifier.width(8.dp))
                        Badge("${summary.steps} steps")
                        Spacer(Modifier.weight(1f))
                        Text(summary.durationMs.formatDuration(), style = MonoStyle, color = mono.textSecondary)
                    }
                    Spacer(Modifier.height(10.dp))
                    KeyValueRow("Started", java.text.SimpleDateFormat("d MMM yyyy · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(summary.startedAt)))
                    KeyValueRow("Provider", summary.provider.ifBlank { "—" })
                    KeyValueRow("Model", summary.model.ifBlank { "—" })
                    KeyValueRow("Session id", summary.id)
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Delete this run", { vm.deleteSession(summary.id) }, color = mono.blood)
                    Spacer(Modifier.height(4.dp))
                    GhostButton("Export to Backblaze B2", { vm.exportSessionToB2(sessionId) })
                    if (b2Message.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            b2Message,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = if (b2Message.startsWith("Exported")) mono.successDot else mono.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            SectionHeader("Trace · ${events.size} events")
        }

        if (events.isEmpty()) {
            item { EmptyState("No trace", "The session file is missing or empty.") }
        } else {
            itemsIndexed(events, key = { index, event -> "${event.t}-$index" }) { _, event ->
                TraceEventRow(event, vm, sessionId)
            }
        }
    }
}

@Composable
private fun TraceEventRow(event: TraceEvent, vm: AuraViewModel, sessionId: String) {
    val mono = LocalMonoScheme.current
    val dot = when {
        event.ok == false || event.type == TraceType.ERROR -> mono.blood
        event.type == TraceType.SESSION_END && event.outcome == SessionOutcome.SUCCESS -> mono.successDot
        event.type == TraceType.ANSWER -> mono.ink
        else -> mono.outlineStrong
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(dot))
            Spacer(Modifier.width(10.dp))
            Text(event.type.replace('_', ' ').uppercase(), style = MonoStyleSmall, color = mono.textSecondary)
            event.durationMs?.let {
                Spacer(Modifier.width(8.dp))
                Text("${it}ms", style = MonoStyleSmall, color = mono.textSecondary)
            }
            Spacer(Modifier.weight(1f))
            Text(
                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(event.t)),
                style = MonoStyleSmall,
                color = mono.textSecondary,
            )
        }
        Spacer(Modifier.height(3.dp))
        event.name?.let {
            Text(it, style = MonoStyle, color = mono.textPrimary)
        }
        val text = event.text ?: event.error
        if (!text.isNullOrBlank()) {
            Text(
                text,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = if (event.type == TraceType.ERROR) mono.blood else mono.textSecondary,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (event.outcome != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                "${event.outcome} · ${event.steps ?: 0} steps",
                style = MonoStyleSmall,
                color = if (event.outcome == SessionOutcome.SUCCESS) mono.successDot else mono.blood,
            )
        }
        event.args?.let {
            Spacer(Modifier.height(3.dp))
            Text(it.toString(), style = MonoStyleSmall, color = mono.textSecondary, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        if (event.type == TraceType.SCREENSHOT && event.path != null) {
            val file = vm.screenshotFor(sessionId, event.path)
            if (file != null) {
                Spacer(Modifier.height(6.dp))
                val bitmap = remember(file.absolutePath) {
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
                }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Step screenshot",
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

private fun beforeFirst(events: List<TraceEvent>): String? =
    events.firstOrNull { it.type == TraceType.SESSION_START }?.text

@Composable
fun RetentionScreen(vm: AuraViewModel) {
    val mono = LocalMonoScheme.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val usage = remember(tick, settings.logRetentionDays) { vm.usageBytes() }

    Column(
        Modifier.fillMaxWidth().statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 32.dp),
    ) {
        Text("Log retention", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Traces, screenshots and the MCP audit log share one retention window.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = mono.textSecondary,
        )
        Spacer(Modifier.height(20.dp))
        SurfaceCard {
            KeyValueRow("Keep logs for", "${settings.logRetentionDays} days")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("-1 day", { vm.updateSettings { it.logRetentionDays = settings.logRetentionDays - 1 } })
                GhostButton("+1 day", { vm.updateSettings { it.logRetentionDays = settings.logRetentionDays + 1 } })
                GhostButton("+7 days", { vm.updateSettings { it.logRetentionDays = settings.logRetentionDays + 7 } })
            }
            Spacer(Modifier.height(12.dp))
            KeyValueRow("On disk", usage.formatBytes())
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InkButton("Prune now", {
                    vm.pruneSessions()
                    tick++
                }, modifier = Modifier.weight(1f))
                com.aura.aura_ui.ui.components.SecondaryButton("Recalculate", {
                    tick++
                }, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(16.dp))
        SurfaceCard {
            Text("What pruning does", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "A background worker runs every 12 hours and removes sessions and audit lines older than the window. Prune now is the same operation, immediately.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            GhostButton("Delete everything now", { confirmClear = true }, color = mono.blood)
            notice?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
            }
        }
        Spacer(Modifier.height(16.dp))
        SurfaceCard {
            Text(
                "Screenshots are never uploaded unless you enabled screenshot attachment and submitted a run. They are pruned with the session.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = mono.textSecondary,
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Delete everything?") },
            text = { Text("All session traces and screenshots on this device will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    vm.clearSessions()
                    tick++
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}
