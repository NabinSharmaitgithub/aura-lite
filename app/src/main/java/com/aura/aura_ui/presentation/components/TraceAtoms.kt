package com.aura.aura_ui.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aura.aura_ui.agent.AgentRunState
import com.aura.aura_ui.agent.ConfirmationRequest
import com.aura.aura_ui.agent.TraceLine
import com.aura.aura_ui.agent.ledger.PlanStepStatus
import com.aura.aura_ui.data.session.SessionOutcome
import com.aura.aura_ui.data.session.SessionSummary
import com.aura.aura_ui.data.session.TraceType
import com.aura.aura_ui.presentation.formatDuration
import com.aura.aura_ui.ui.components.Badge
import com.aura.aura_ui.ui.components.ChipTone
import com.aura.aura_ui.ui.components.Dot
import com.aura.aura_ui.ui.components.GhostButton
import com.aura.aura_ui.ui.components.InkCard
import com.aura.aura_ui.ui.components.PausePill
import com.aura.aura_ui.ui.components.SecondaryButton
import com.aura.aura_ui.ui.components.StatusStrip
import com.aura.aura_ui.ui.components.SurfaceCard
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyle
import com.aura.aura_ui.ui.theme.MonoStyleSmall

fun traceTone(type: String): ChipTone = when (type) {
    TraceType.ERROR, TraceType.INTERRUPT -> ChipTone.Broken
    TraceType.TOOL_RESULT, TraceType.VERIFY, TraceType.ANSWER -> ChipTone.Live
    else -> ChipTone.Idle
}

@Composable
fun TraceLineRow(line: TraceLine, modifier: Modifier = Modifier, showTime: Boolean = false) {
    val mono = LocalMonoScheme.current
    val dot = when {
        line.ok == false -> mono.blood
        line.ok == true -> mono.successDot
        line.type == TraceType.ERROR -> mono.blood
        line.type == TraceType.ANSWER -> mono.ink
        else -> mono.outlineStrong
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Dot(dot, size = 7.dp, modifier = Modifier.padding(top = 5.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    line.type.uppercase(),
                    style = MonoStyleSmall,
                    color = mono.textSecondary,
                )
                if (showTime) {
                    Spacer(Modifier.width(8.dp))
                    Text(java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(line.at)), style = MonoStyleSmall, color = mono.textSecondary)
                }
                line.durationMs?.let {
                    Spacer(Modifier.width(8.dp))
                    Text("${it}ms", style = MonoStyleSmall, color = mono.textSecondary)
                }
            }
            Text(
                line.title,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = if (line.type == TraceType.ERROR) mono.blood else mono.textPrimary,
            )
            if (!line.detail.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    line.detail,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!line.args.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    line.args,
                    style = MonoStyleSmall,
                    color = mono.textSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun LiveRunCard(
    state: AgentRunState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoScheme.current
    val statusTone = when {
        state.paused -> ChipTone.Idle
        state.running -> ChipTone.Live
        state.error != null || state.outcome == SessionOutcome.FAILURE -> ChipTone.Broken
        else -> ChipTone.On
    }
    InkCard(modifier = modifier) {
        StatusStrip(
            status = state.displayStatus.uppercase(),
            detail = state.trace.lastOrNull()?.title?.take(72),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (statusTone == ChipTone.Broken) mono.blood else mono.successDot))
            Spacer(Modifier.width(8.dp))
            Text(
                state.displayStatus.uppercase(),
                style = MonoStyleSmall,
                color = mono.textOnInkDim,
            )
            Spacer(Modifier.weight(1f))
            if (state.stepCount > 0) {
                Text("${state.stepCount} steps", style = MonoStyleSmall, color = mono.textOnInkDim)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            state.goal,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = mono.textOnInk,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (!state.streamText.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                state.streamText,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = mono.textOnInkDim,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (state.planSteps.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            RunTodoList(state = state, modifier = Modifier.fillMaxWidth())
        }
        if (state.trace.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            state.trace.takeLast(4).forEach { line ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    Dot(if (line.ok == false) mono.blood else mono.textOnInkDim, size = 5.dp, modifier = Modifier.padding(top = 5.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        line.title,
                        style = MonoStyleSmall,
                        color = if (line.ok == false) mono.blood else mono.textOnInkDim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (!state.answer.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(state.answer, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = mono.textOnInk)
        }
        if (state.error != null && state.answer == null) {
            Spacer(Modifier.height(12.dp))
            Text(state.error, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = mono.blood)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.running) {
                PausePill(
                    paused = state.paused,
                    onTogglePause = { if (state.paused) onResume() else onPause() },
                    onStop = onStop,
                )
            } else {
                GhostButton("Dismiss", onDismiss, color = mono.textOnInkDim)
            }
        }
    }
}

/**
 * Cursor-style to-do list for the running agent: a collapsible header with done / in-progress /
 * pending item states, fed by the run ledger plan steps.
 */
@Composable
fun RunTodoList(
    state: AgentRunState,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoScheme.current
    val steps = state.planSteps
    if (steps.isEmpty()) return
    var expanded by remember { mutableStateOf(true) }
    val doneCount = steps.count { it.status == "done" }
    val activeStep = steps.firstOrNull { it.status == "in_progress" }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("TODO", style = MonoStyleSmall, color = mono.textSecondary)
            Spacer(Modifier.weight(1f))
            Text(
                buildString {
                    append("${steps.size} steps")
                    if (doneCount > 0) append(" · $doneCount done")
                    if (activeStep != null) append(" · in progress")
                },
                style = MonoStyleSmall,
                color = mono.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(if (expanded) "▾" else "▸", style = MonoStyleSmall, color = mono.textSecondary)
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            steps.forEach { step ->
                val stepColor = when (step.typedStatus) {
                    PlanStepStatus.DONE -> mono.successDot
                    PlanStepStatus.IN_PROGRESS -> mono.blood
                    PlanStepStatus.BLOCKED -> mono.blood
                    PlanStepStatus.SKIPPED -> mono.textSecondary
                    PlanStepStatus.PENDING -> mono.outlineStrong
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Dot(stepColor, size = 7.dp, modifier = Modifier.padding(top = 5.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            step.description,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = mono.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val note = step.note
                        if (!note.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                note,
                                style = MonoStyleSmall,
                                color = mono.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        when (step.typedStatus) {
                            PlanStepStatus.DONE -> "DONE"
                            PlanStepStatus.IN_PROGRESS -> "NOW"
                            PlanStepStatus.BLOCKED -> "BLOCKED"
                            PlanStepStatus.SKIPPED -> "SKIPPED"
                            PlanStepStatus.PENDING -> "PENDING"
                        },
                        style = MonoStyleSmall,
                        color = when (step.typedStatus) {
                            PlanStepStatus.DONE -> mono.successDot
                            PlanStepStatus.IN_PROGRESS -> mono.blood
                            PlanStepStatus.BLOCKED -> mono.blood
                            PlanStepStatus.SKIPPED -> mono.textSecondary
                            PlanStepStatus.PENDING -> mono.outlineStrong
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun AskUserCard(
    request: ConfirmationRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoScheme.current
    SurfaceCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CONFIRM", style = MonoStyleSmall, color = mono.blood)
            Spacer(Modifier.weight(1f))
            request.tool?.let { Badge(it, tone = ChipTone.Broken) }
        }
        Spacer(Modifier.height(8.dp))
        Text(request.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(request.detail, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Deny", onDeny, Modifier.weight(1f))
            com.aura.aura_ui.ui.components.InkButton(
                label = "Allow once",
                onClick = onApprove,
                modifier = Modifier.weight(1f),
                fillWidth = true,
            )
        }
    }
}

fun outcomeTone(outcome: String?): ChipTone = when (outcome) {
    SessionOutcome.SUCCESS -> ChipTone.Live
    SessionOutcome.FAILURE, SessionOutcome.PERMISSION_DENIED -> ChipTone.Broken
    else -> ChipTone.Idle
}

fun outcomeLabel(outcome: String?): String = when (outcome) {
    SessionOutcome.SUCCESS -> "done"
    SessionOutcome.FAILURE -> "failed"
    SessionOutcome.CANCELLED -> "stopped"
    SessionOutcome.BUDGET_EXHAUSTED -> "budget"
    SessionOutcome.PERMISSION_DENIED -> "permission"
    null -> "running"
    else -> outcome
}

@Composable
fun SessionRow(
    session: SessionSummary,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoScheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(composeShape())
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(if (session.isRunning) mono.successDot else if (session.outcome == SessionOutcome.SUCCESS) mono.successDot else if (session.outcome == SessionOutcome.FAILURE) mono.blood else mono.outlineStrong, size = 7.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                session.goal.ifBlank { "Untitled run" },
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = mono.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(outcomeLabel(session.outcome), style = MonoStyleSmall, color = mono.textSecondary)
                Text(" · ", style = MonoStyleSmall, color = mono.textSecondary)
                Text("${session.steps} steps", style = MonoStyleSmall, color = mono.textSecondary)
                Text(" · ", style = MonoStyleSmall, color = mono.textSecondary)
                Text(session.durationMs.formatDuration(), style = MonoStyleSmall, color = mono.textSecondary)
            }
            if (session.provider.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "${session.provider}${if (session.model.isNotBlank()) " · ${session.model}" else ""}",
                    style = MonoStyleSmall,
                    color = mono.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onDelete != null) {
            Spacer(Modifier.width(8.dp))
            GhostButton("Delete", onDelete, color = mono.blood)
        }
    }
}

internal fun composeShape() = androidx.compose.foundation.shape.RoundedCornerShape(com.aura.aura_ui.ui.theme.ShapeTokens.CardSmall)
