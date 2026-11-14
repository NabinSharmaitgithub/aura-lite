package com.aura.aura_ui.presentation.screens

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.aura_ui.presentation.AuraViewModel
import com.aura.aura_ui.presentation.rememberPermissions
import com.aura.aura_ui.services.WakeWordListeningService
import com.aura.aura_ui.state.WakeWordState
import com.aura.aura_ui.ui.components.AuraSwitch
import com.aura.aura_ui.ui.components.Badge
import com.aura.aura_ui.ui.components.ChipTone
import com.aura.aura_ui.ui.components.GhostButton
import com.aura.aura_ui.ui.components.InkButton
import com.aura.aura_ui.ui.components.KeyValueRow
import com.aura.aura_ui.ui.components.PermissionBanner
import com.aura.aura_ui.ui.components.SecondaryButton
import com.aura.aura_ui.ui.components.SectionHeader
import com.aura.aura_ui.ui.components.SurfaceCard
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.Mono
import com.aura.aura_ui.ui.theme.MonoStyle
import com.aura.aura_ui.ui.theme.MonoStyleSmall
import com.aura.aura_ui.voice.SherpaKwsWakeWordDetector
import com.aura.aura_ui.voice.WakeWordEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated Wake Word Configuration & Live Testing Screen.
 *
 * Provides master controls, live audio feedback testing sandbox,
 * sensitivity threshold sliders, and diagnostic health checks.
 */
@Composable
fun WakeWordScreen(
    vm: AuraViewModel,
    onNavigateBack: () -> Unit = {},
) {
    val mono = LocalMonoScheme.current
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val permissions = rememberPermissions(vm)
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val scope = rememberCoroutineScope()

    val modelsPresent = remember { WakeWordEngine.modelsPresent(context) }
    val wakeServiceState = WakeWordListeningService.stateFlow.collectAsStateWithLifecycle().value

    var threshold by remember(settings.wakeWordThreshold) { mutableFloatStateOf(settings.wakeWordThreshold) }
    var score by remember(settings.wakeWordScore) { mutableFloatStateOf(settings.wakeWordScore) }

    // Live testing states
    var isTestingActive by remember { mutableStateOf(false) }
    var testAudioLevel by remember { mutableFloatStateOf(0f) }
    var testDetectionResult by remember { mutableStateOf<String?>(null) }
    var testDetector by remember { mutableStateOf<SherpaKwsWakeWordDetector?>(null) }

    // Pulsing animation for active listening
    val infiniteTransition = rememberInfiniteTransition(label = "wake_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    // Clean up test detector on exit
    DisposableEffect(Unit) {
        onDispose {
            testDetector?.stop()
            testDetector?.release()
            testDetector = null
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Top Navigation Bar ──────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = mono.textPrimary,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Wake Word",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = mono.textPrimary,
                    )
                }

                Badge(
                    text = if (modelsPresent) "sherpa-onnx KWS" else "Model Missing",
                    tone = if (modelsPresent) ChipTone.Live else ChipTone.Broken,
                )
            }
        }

        // ── Microphone Permission Warning ──────────────────────────────────
        if (!permissions.microphone) {
            item {
                PermissionBanner(
                    text = "Microphone access is required for offline wake word detection.",
                    action = "Grant",
                    onAction = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                )
            }
        }

        // ── Main Hero Card: Status & Master Switch ───────────────────────────
        item {
            SurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        // Pulsing status dot
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(44.dp),
                        ) {
                            if (settings.wakeWordEnabled && permissions.microphone) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(Color(0xFF34C759).copy(alpha = 0.25f)),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            !settings.wakeWordEnabled -> mono.textSecondary.copy(alpha = 0.3f)
                                            !permissions.microphone -> Color(0xFFFF3B30)
                                            wakeServiceState == WakeWordState.DETECTING -> Color(0xFF34C759)
                                            wakeServiceState == WakeWordState.PAUSED_FOR_RUN -> Color(0xFFFF9500)
                                            else -> Color(0xFF34C759)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Mic,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "“Wake up AURA”",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = mono.textPrimary,
                            )
                            Text(
                                text = when {
                                    !modelsPresent -> "Models not loaded from assets/kws"
                                    !permissions.microphone -> "Microphone permission required"
                                    !settings.wakeWordEnabled -> "Detection disabled"
                                    wakeServiceState == WakeWordState.DETECTING -> "Listening continuously in background"
                                    wakeServiceState == WakeWordState.PAUSED_FOR_RUN -> "Paused during voice action"
                                    else -> "Service active & ready"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (settings.wakeWordEnabled && permissions.microphone) Color(0xFF34C759) else mono.textSecondary,
                            )
                        }
                    }

                    AuraSwitch(
                        checked = settings.wakeWordEnabled,
                        onCheckedChange = { vm.setWakeWord(it) },
                        enabled = modelsPresent && permissions.microphone,
                    )
                }
            }
        }

        // ── Live Interactive Wake Word Tester ───────────────────────────────
        item {
            SectionHeader("Live Interactive Tester")
            SurfaceCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Test recognition on your device without leaving this screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = mono.textSecondary,
                    )

                    Spacer(Modifier.height(14.dp))

                    // Audio Level Meter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Audio Mic:",
                            style = MonoStyleSmall,
                            color = mono.textSecondary,
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(mono.outline),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (isTestingActive) testAudioLevel.coerceIn(0.05f, 1f) else 0f)
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (testDetectionResult != null) Color(0xFF34C759) else Mono.Ink),
                            )
                        }
                        Text(
                            text = if (isTestingActive) "${(testAudioLevel * 100).toInt()}%" else "Idle",
                            style = MonoStyleSmall,
                            color = mono.textSecondary,
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Recognition Feedback Banner
                    AnimatedVisibility(
                        visible = testDetectionResult != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF34C759).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF34C759), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Wake Phrase Recognized!",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34C759),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "Detected: \"${testDetectionResult}\"",
                                    style = MonoStyleSmall,
                                    color = mono.textPrimary,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (!isTestingActive) {
                            InkButton(
                                label = "Start Test Mode",
                                onClick = {
                                    if (!permissions.microphone) {
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        return@InkButton
                                    }
                                    testDetectionResult = null
                                    isTestingActive = true

                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val d = SherpaKwsWakeWordDetector(
                                                context = context,
                                                keywordsScore = score,
                                                keywordsThreshold = threshold,
                                            )
                                            testDetector = d
                                            d.setOnWakeWordDetected { recognized ->
                                                scope.launch(Dispatchers.Main) {
                                                    testDetectionResult = recognized
                                                    delay(3500)
                                                    testDetectionResult = null
                                                }
                                            }
                                            d.start()

                                            // Observe audio peak
                                            while (isActive && isTestingActive) {
                                                testAudioLevel = d.lastAudioLevel.value
                                                delay(60)
                                            }
                                        } catch (e: Exception) {
                                            isTestingActive = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            SecondaryButton(
                                label = "Stop Test",
                                onClick = {
                                    isTestingActive = false
                                    testDetector?.stop()
                                    testDetector?.release()
                                    testDetector = null
                                    testAudioLevel = 0f
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        SecondaryButton(
                            label = "Test Trigger",
                            onClick = {
                                vm.showNotice("Simulated Wake Word: 'Wake up AURA'")
                                com.aura.aura_ui.services.AssistantForegroundService.listen(context)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (isTestingActive) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Say “Wake up AURA” into the microphone...",
                            style = MonoStyleSmall,
                            color = mono.textSecondary,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        }

        // ── Sensitivity & Acoustic Tuning ───────────────────────────────────
        item {
            SectionHeader("Sensitivity & Tuning")
            SurfaceCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Trigger Threshold",
                            style = MaterialTheme.typography.titleMedium,
                            color = mono.textPrimary,
                        )
                        Badge(
                            text = String.format("%.2f", threshold) + if (threshold == 0.20f) " (Default)" else "",
                            tone = ChipTone.Idle,
                        )
                    }
                    Text(
                        text = "Lower = easier detection; Higher = stricter against background chatter.",
                        style = MaterialTheme.typography.bodySmall,
                        color = mono.textSecondary,
                    )
                    Slider(
                        value = threshold,
                        onValueChange = { threshold = it },
                        valueRange = 0.10f..0.40f,
                        steps = 5,
                        onValueChangeFinished = {
                            vm.updateSettings { it.wakeWordThreshold = threshold }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Mono.Ink,
                            activeTrackColor = Mono.Ink,
                        ),
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Keyword Boosting Score",
                            style = MaterialTheme.typography.titleMedium,
                            color = mono.textPrimary,
                        )
                        Badge(
                            text = String.format("%.1f", score) + if (score == 1.5f) " (Default)" else "",
                            tone = ChipTone.Idle,
                        )
                    }
                    Text(
                        text = "Acoustic score bonus applied to the 'Wake up AURA' phoneme sequence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = mono.textSecondary,
                    )
                    Slider(
                        value = score,
                        onValueChange = { score = it },
                        valueRange = 1.0f..3.0f,
                        steps = 7,
                        onValueChangeFinished = {
                            vm.updateSettings { it.wakeWordScore = score }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Mono.Ink,
                            activeTrackColor = Mono.Ink,
                        ),
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GhostButton("Sensitive (0.15)", {
                            threshold = 0.15f
                            score = 2.0f
                            vm.updateSettings { it.wakeWordThreshold = 0.15f; it.wakeWordScore = 2.0f }
                        }, modifier = Modifier.weight(1f))

                        GhostButton("Balanced (0.20)", {
                            threshold = 0.20f
                            score = 1.5f
                            vm.updateSettings { it.wakeWordThreshold = 0.20f; it.wakeWordScore = 1.5f }
                        }, modifier = Modifier.weight(1f))

                        GhostButton("Strict (0.30)", {
                            threshold = 0.30f
                            score = 1.2f
                            vm.updateSettings { it.wakeWordThreshold = 0.30f; it.wakeWordScore = 1.2f }
                        }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // ── Phonetic & Acoustic Sequence Info ────────────────────────────────
        item {
            SectionHeader("Acoustic Model Details")
            SurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Phonetic Keyword Decomposition",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = mono.textPrimary,
                    )
                    Text(
                        text = "The transducer recognizes variants of 'Wake up AURA' mapped to BPE tokens:",
                        style = MaterialTheme.typography.bodySmall,
                        color = mono.textSecondary,
                    )
                    Spacer(Modifier.height(2.dp))

                    val phonemes = listOf(
                        "▁WA KE  ▁UP  ▁A UR A" to "Primary standard vocalization",
                        "▁WA KE  ▁UP  ▁OR A H" to "Common alternative phoneme catch",
                        "▁WA KE  ▁UP  ▁OUR A" to "Blended diphthong pronunciation",
                    )
                    phonemes.forEach { (seq, note) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(mono.outline.copy(alpha = 0.5f))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(seq, style = MonoStyle, fontWeight = FontWeight.Bold, color = mono.textPrimary)
                            Text(note, style = MaterialTheme.typography.labelSmall, color = mono.textSecondary)
                        }
                    }
                }
            }
        }

        // ── Engine Diagnostics & Health Checklist ───────────────────────────
        item {
            SectionHeader("Engine Diagnostics")
            SurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyValueRow("Model Engine", "sherpa-onnx Zipformer2 (3.3M params)")
                    KeyValueRow("Quantization", "INT8 Static Link (ONNX Runtime)")
                    KeyValueRow("Audio Format", "16,000 Hz · Mono · 16-bit PCM")
                    KeyValueRow("CPU Usage", "< 1% (Energy efficient idle)")
                    KeyValueRow("Cloud Telemetry", "None (100% Offline & Private)")
                    KeyValueRow(
                        "Model Asset Status",
                        if (modelsPresent) "Installed (assets/kws) ✅" else "Missing ❌",
                    )
                    KeyValueRow(
                        "Microphone Grant",
                        if (permissions.microphone) "Granted ✅" else "Not Granted ❌",
                    )
                }
            }
        }
    }
}
