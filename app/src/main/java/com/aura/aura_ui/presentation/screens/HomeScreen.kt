package com.aura.aura_ui.presentation.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.aura_ui.agent.AgentRunState
import com.aura.aura_ui.presentation.AuraViewModel
import com.aura.aura_ui.presentation.components.AskUserCard
import com.aura.aura_ui.presentation.components.LiveRunCard
import com.aura.aura_ui.presentation.components.SessionRow
import com.aura.aura_ui.presentation.navigation.Routes
import com.aura.aura_ui.presentation.rememberPermissions
import com.aura.aura_ui.ui.components.AuraTextField
import com.aura.aura_ui.ui.components.ChipTone
import com.aura.aura_ui.ui.components.EmptyState
import com.aura.aura_ui.ui.components.GhostButton
import com.aura.aura_ui.ui.components.GroupedList
import com.aura.aura_ui.ui.components.InkButton
import com.aura.aura_ui.ui.components.PermissionBanner
import com.aura.aura_ui.ui.components.StatusChip
import com.aura.aura_ui.ui.components.SurfaceCard
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyle
import com.aura.aura_ui.ui.theme.MonoStyleSmall
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val suggestions = listOf(
    "Reply to my last WhatsApp message",
    "Turn on Do Not Disturb for an hour",
    "Find the PDF I downloaded yesterday",
    "What is on my screen right now?",
    "Set a reminder to call Mom at 6",
    "Open the Play Store page for Chrome",
    "Summarise my notifications",
    "Pause whatever is playing",
)

@Composable
fun AuraHomeScreen(
    vm: AuraViewModel,
    onNavigate: (String) -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val mono = LocalMonoScheme.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val agent by vm.agent.collectAsStateWithLifecycle()
    val confirmation by vm.confirmation.collectAsStateWithLifecycle()
    val sessions by vm.recentSessions.collectAsStateWithLifecycle()
    val health by vm.mcpHealth.collectAsStateWithLifecycle()
    val listening by vm.voiceListening.collectAsStateWithLifecycle()
    val permissions = rememberPermissions(vm)
    val askPending by vm.askPending.collectAsStateWithLifecycle()
    val interruptedGoal by vm.interruptedGoal.collectAsStateWithLifecycle()

    var goal by remember { mutableStateOf("") }
    val endpoint = vm.currentEndpoint()
    val hasKey = vm.hasKey(endpoint)
    val greeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            hour < 5 -> "Still up"
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Box(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (interruptedGoal != null && !agent.running) {
                item {
                    InterruptedRunCard(
                        goal = interruptedGoal!!,
                        onResume = { vm.resumeInterruptedRun() },
                        onDismiss = { vm.dismissInterruptedRun() },
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
            if (askPending != null) {
                item {
                    PendingQuestionCard(
                        question = askPending!!.question,
                        options = askPending!!.options,
                        onAnswer = { vm.answerAsk(it) },
                        onDismiss = { vm.dismissAsk() },
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (settings.userName.isBlank()) greeting else "$greeting, ${settings.userName}",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = mono.textSecondary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "What should I do?",
                            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                            color = mono.textPrimary,
                        )
                    }
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(mono.ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = mono.textOnInk, modifier = Modifier.size(24.dp))
                    }
                }
            }

            if (!vm.isOnline()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    PermissionBanner(
                        text = "You are offline. Planning still works but tools that need the network will fail fast.",
                        action = "Settings",
                        onAction = { onNavigate(Routes.SETTINGS) },
                    )
                }
            }

            if (!hasKey) {
                item {
                    Spacer(Modifier.height(16.dp))
                    PermissionBanner(
                        text = "No API key for ${endpoint.displayName}. Add one to let AURA think.",
                        action = "Open Brain",
                        onAction = { onNavigate(Routes.AGENT_BRAIN) },
                    )
                }
            }

            if (!permissions.accessibility) {
                item {
                    Spacer(Modifier.height(16.dp))
                    PermissionBanner(
                        text = "Accessibility is off, so AURA cannot read or touch the screen.",
                        action = "Fix",
                        onAction = { onNavigate(Routes.SETTINGS_PERMISSIONS) },
                    )
                }
            }

            item {
                Spacer(Modifier.height(22.dp))
                SurfaceCard(contentPadding = PaddingValues(18.dp)) {
                    AuraTextField(
                        value = goal,
                        onValueChange = { goal = it },
                        placeholder = "Tell AURA what to do…",
                        singleLine = false,
                        minHeight = 104.dp,
                        textStyle = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (listening) mono.ink else mono.surfaceElevated)
                                .clickable { vm.startListening() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Mic,
                                contentDescription = "Voice input",
                                tint = if (listening) mono.textOnInk else mono.textPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (listening) "Listening… tap the pill to stop" else "Voice",
                            style = MonoStyle,
                            color = mono.textSecondary,
                        )
                        Spacer(Modifier.weight(1f))
                        InkButton(
                            label = if (agent.running) "Running…" else "Run",
                            onClick = {
                                val text = goal.trim()
                                if (text.isNotEmpty()) {
                                    vm.submit(text)
                                    goal = ""
                                }
                            },
                            modifier = Modifier.width(132.dp),
                            enabled = hasKey && !agent.running && goal.isNotBlank(),
                            leading = Icons.Outlined.ArrowForward,
                            fillWidth = false,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { prompt ->
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(mono.surface)
                                .clickable {
                                    goal = prompt
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        ) {
                            Text(prompt, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = mono.textSecondary, maxLines = 1)
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip(
                        label = if (permissions.accessibility) "Screen" else "Screen off",
                        tone = if (permissions.accessibility) ChipTone.Live else ChipTone.Broken,
                        onClick = { onNavigate(Routes.SETTINGS_PERMISSIONS) },
                    )
                    StatusChip(
                        label = if (permissions.screenCapture) "Capture" else "No capture",
                        tone = if (permissions.screenCapture) ChipTone.Live else ChipTone.Idle,
                        onClick = { onNavigate(Routes.SETTINGS_PERMISSIONS) },
                    )
                    StatusChip(
                        label = if (settings.wakeWordEnabled) "Wake word" else "Voice",
                        tone = if (settings.wakeWordEnabled) ChipTone.Live else ChipTone.Idle,
                        onClick = { onNavigate(Routes.AGENT_VOICE) },
                    )
                    StatusChip(
                        label = if (health.running) "MCP" else "MCP off",
                        tone = if (health.running) ChipTone.Live else ChipTone.Idle,
                        count = if (health.running) health.clientCount else null,
                        onClick = { onNavigate(Routes.SETTINGS_TRUSTED_DEVICES) },
                    )
                }
            }

            if (confirmation != null) {
                item {
                    Spacer(Modifier.height(18.dp))
                    AskUserCard(
                        request = confirmation!!,
                        onApprove = { vm.approveConfirmation() },
                        onDeny = { vm.denyConfirmation() },
                    )
                }
            }

            if (agent.running || agent.goal.isNotBlank()) {
                item {
                    Spacer(Modifier.height(18.dp))
                    LiveRunCard(
                        state = agent,
                        onPause = { vm.pauseRun() },
                        onResume = { vm.resumeRun() },
                        onStop = { vm.stopRun() },
                        onDismiss = { vm.dismissRun() },
                    )
                    if (agent.sessionId != null) {
                        Spacer(Modifier.height(6.dp))
                        GhostButton("Open full trace", { onOpenSession(agent.sessionId!!) }, color = mono.textPrimary)
                    }
                }
            }

            item {
                Spacer(Modifier.height(28.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent runs", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = mono.textPrimary, modifier = Modifier.weight(1f))
                    GhostButton("All", { onNavigate(Routes.ACTIVITY) })
                }
                Spacer(Modifier.height(8.dp))
            }

            if (sessions.isEmpty()) {
                item {
                    EmptyState(
                        title = "No runs yet",
                        detail = "Ask for something above. Every step will be recorded here.",
                    )
                }
            } else {
                item {
                    SurfaceCard(contentPadding = PaddingValues(0.dp)) {
                        sessions.take(5).forEachIndexed { index, session ->
                            SessionRow(
                                session = session,
                                onClick = { onOpenSession(session.id) },
                            )
                            if (index < minOf(4, sessions.lastIndex)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp)
                                        .height(1.dp)
                                        .background(mono.outline),
                                )
                            }
                        }
                    }
                }
            }
        }
        AgentCursorOverlay(state = agent)
    }
}



@Composable
private fun AgentCursorOverlay(state: AgentRunState) {
    val cursor = state.cursor ?: return
    if (!state.running) return
    val mono = LocalMonoScheme.current
    // Animate cursor motion so it visibly moves across the screen.
    val targetX = cursor.x
    val targetY = cursor.y
    val animX by animateFloatAsState(
        targetValue = targetX,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "cursor-x",
    )
    val animY by animateFloatAsState(
        targetValue = targetY,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "cursor-y",
    )
    // Show a motion pulse when the cursor moves to a new point.
    var moving by remember { mutableStateOf(false) }
    LaunchedEffect(targetX, targetY) {
        moving = true
        delay(520)
        moving = false
    }
    val pulseAlpha by animateFloatAsState(
        targetValue = if (moving) 0.45f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "cursor-pulse",
    )
    val pulseScale by animateFloatAsState(
        targetValue = if (moving) 1.6f else 1f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "cursor-scale",
    )
    Box(Modifier.fillMaxSize()) {
        // Moving trail/pulse ring (appears while the cursor is in motion).
        if (pulseAlpha > 0.01f) {
            Box(
                Modifier
                    .offset { IntOffset(animX.roundToInt() - 22, animY.roundToInt() - 22) }
                    .size(44.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .alpha(pulseAlpha)
                    .border(2.dp, mono.blood, CircleShape),
            )
        }
        // Cursor: outer ring + inner dot indicating the click point.
        Box(
            Modifier
                .offset { IntOffset(animX.roundToInt() - 14, animY.roundToInt() - 14) }
                .size(28.dp)
                .border(2.dp, mono.blood, CircleShape)
                .padding(7.dp)
                .clip(CircleShape)
                .background(mono.blood.copy(alpha = 0.85f)),
        )
    }
}

@Composable
private fun InterruptedRunCard(
    goal: String,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mono = LocalMonoScheme.current
    SurfaceCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "A run was interrupted",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = mono.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    goal,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Dismiss", onDismiss, color = mono.blood)
            InkButton("Resume", onResume, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PendingQuestionCard(
    question: String,
    options: List<String>,
    onAnswer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mono = LocalMonoScheme.current
    var freeText by remember { mutableStateOf("") }
    SurfaceCard(contentPadding = PaddingValues(18.dp)) {
        Text(
            "AURA ASKS",
            style = MonoStyleSmall,
            color = mono.textSecondary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            question,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = mono.textPrimary,
        )
        if (options.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    InkButton(option, { onAnswer(option) }, modifier = Modifier.fillMaxWidth(), fillWidth = true)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        AuraTextField(
            value = freeText,
            onValueChange = { freeText = it },
            placeholder = "Or type an answer…",
            textStyle = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Skip", onDismiss, color = mono.blood)
            InkButton(
                "Send",
                { if (freeText.isNotBlank()) onAnswer(freeText.trim()) },
                modifier = Modifier.weight(1f),
                enabled = freeText.isNotBlank(),
            )
        }
    }
}
