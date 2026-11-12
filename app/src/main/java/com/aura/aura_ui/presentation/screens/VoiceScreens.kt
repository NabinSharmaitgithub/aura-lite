package com.aura.aura_ui.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.aura_ui.presentation.AuraViewModel
import com.aura.aura_ui.presentation.rememberPermissions
import com.aura.aura_ui.voice.WakeWordEngine
import com.aura.aura_ui.ui.components.AuraSwitch
import com.aura.aura_ui.ui.components.AuraTextField
import com.aura.aura_ui.ui.components.Badge
import com.aura.aura_ui.ui.components.ChipTone
import com.aura.aura_ui.ui.components.GhostButton
import com.aura.aura_ui.ui.components.GroupedList
import com.aura.aura_ui.ui.components.GroupedRow
import com.aura.aura_ui.ui.components.InkButton
import com.aura.aura_ui.ui.components.KeyValueRow
import com.aura.aura_ui.ui.components.PermissionBanner
import com.aura.aura_ui.ui.components.SecondaryButton
import com.aura.aura_ui.ui.components.SectionHeader
import com.aura.aura_ui.ui.components.SurfaceCard
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyle
import com.aura.aura_ui.ui.theme.MonoStyleSmall

@Composable
fun CompanionScreen(vm: AuraViewModel) {
    val mono = LocalMonoScheme.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val permissions = rememberPermissions(vm)
    val notice by vm.notice.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Companion", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "A quieter, always-available voice mode. Off by default and never starts a microphone by itself.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            if (!permissions.overlay) {
                PermissionBanner(
                    text = "Display over other apps is required for the floating companion.",
                    action = "Fix",
                    onAction = { vm.showNotice("Open Settings → Permissions to grant overlay access.") },
                )
                Spacer(Modifier.height(14.dp))
            }
            GroupedList(header = "Presence") {
                GroupedRow(
                    title = "Companion overlay",
                    subtitle = "Floating pill with live transcript and cancel",
                    icon = Icons.Outlined.RecordVoiceOver,
                    trailing = {
                        AuraSwitch(
                            checked = settings.companionEnabled,
                            onCheckedChange = { vm.setCompanionEnabled(it) },
                            enabled = permissions.overlay,
                        )
                    },
                    showChevron = false,
                )
                GroupedRow(
                    title = "Live UI stream",
                    subtitle = "Show what AURA is doing while it acts",
                    icon = Icons.Outlined.Visibility,
                    trailing = {
                        AuraSwitch(
                            checked = settings.uiStreamEnabled,
                            onCheckedChange = { vm.updateSettings { s -> s.uiStreamEnabled = it } },
                        )
                    },
                    showChevron = false,
                )
                GroupedRow(
                    title = "Edge glow",
                    subtitle = "A thin red edge pulse when AURA touches the screen",
                    icon = Icons.Outlined.GraphicEq,
                    trailing = {
                        AuraSwitch(
                            checked = settings.edgeGlowEnabled,
                            onCheckedChange = { vm.updateSettings { s -> s.edgeGlowEnabled = it } },
                        )
                    },
                    showChevron = false,
                    isLast = true,
                )
            }
            Spacer(Modifier.height(20.dp))
            GroupedList(header = "Voice character") {
                GroupedRow(
                    title = "Recordable voice",
                    subtitle = "Use a locally recorded reply voice where the engine supports it",
                    icon = Icons.Outlined.Mic,
                    trailing = {
                        AuraSwitch(
                            checked = settings.recordableVoiceEnabled,
                            onCheckedChange = { vm.updateSettings { s -> s.recordableVoiceEnabled = it } },
                        )
                    },
                    showChevron = false,
                )
                GroupedRow(
                    title = "Attach screenshots",
                    subtitle = "Send a downscaled screenshot with a model request",
                    icon = Icons.Outlined.Visibility,
                    trailing = {
                        AuraSwitch(
                            checked = settings.readScreenImageEnabled,
                            onCheckedChange = { vm.updateSettings { s -> s.readScreenImageEnabled = it } },
                        )
                    },
                    showChevron = false,
                    isLast = true,
                )
            }
            Spacer(Modifier.height(18.dp))
            SurfaceCard {
                Text("Companion is not a subscription", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "There is no account, no wake-word cloud and no analytics. The overlay is a local service you can stop at any time.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
                notice?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                }
            }
        }
    }
}

@Composable
fun VoiceSettingsScreen(vm: AuraViewModel) {
    val mono = LocalMonoScheme.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val permissions = rememberPermissions(vm)
    val notice by vm.notice.collectAsStateWithLifecycle()
    var voiceName by remember(settings.ttsVoice) { mutableStateOf(settings.ttsVoice) }
    var testLine by remember { mutableStateOf("This is AURA. I am ready.") }
    val voices = remember { vm.ttsVoices() }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Voice", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Offline wake word (\"Wake up AURA\", via sherpa-onnx when model is installed), volume-key shortcut, Android assistant gesture, speech-to-text, spoken replies, and Gemini Live conversation mode.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            GroupedList(header = "Listening") {
                GroupedRow(
                    title = "Wake word",
                    subtitle = when {
                        !vm.wakeWordAvailable -> "Model not bundled · add assets/kws (expected: Wake up AURA)"
                        !permissions.microphone -> "Microphone permission needed"
                        settings.wakeWordEnabled -> "Listening for “${WakeWordEngine.configuredKeywordLabel(LocalContext.current)}”"
                        else -> "Off (${WakeWordEngine.configuredKeywordLabel(LocalContext.current)})"
                    },
                    icon = Icons.Outlined.Mic,
                    trailing = {
                        AuraSwitch(
                            checked = settings.wakeWordEnabled,
                            onCheckedChange = { vm.setWakeWord(it) },
                            enabled = vm.wakeWordAvailable && permissions.microphone,
                        )
                    },
                    showChevron = false,
                )
                GroupedRow(
                    title = "Speech recognition",
                    subtitle = if (vm.speechAvailable) "Platform recognizer available" else "No recognizer on this device",
                    icon = Icons.Outlined.RecordVoiceOver,
                    trailing = { Badge(if (vm.speechAvailable) "ready" else "missing", tone = if (vm.speechAvailable) ChipTone.Live else ChipTone.Broken) },
                    showChevron = false,
                    isLast = true,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (!vm.wakeWordAvailable) {
                SurfaceCard {
                    Text(
                        "Wake word is disabled because the sherpa-onnx keyword model is not present in this source tree. Put encoder.onnx, decoder.onnx, joiner.onnx, tokens.txt and keywords.txt under assets/kws/ (configured for “Wake up AURA”) and rebuild. The service will never start without them.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = mono.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionHeader("Silero VAD sensitivity")
            SurfaceCard {
                KeyValueRow("Sensitivity", "${settings.vadSensitivity} / 4")
                Slider(
                    value = settings.vadSensitivity.toFloat(),
                    onValueChange = { vm.updateSettings { s -> s.vadSensitivity = it.toInt() } },
                    valueRange = 0f..4f,
                    steps = 3,
                )
                Text(
                    "Higher values keep the microphone open longer before deciding you stopped speaking.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionHeader("Speaking voice (TTS)")
            SurfaceCard {
                Text(
                    "Use system default, Google TTS (enter \"google\"), or an engine/voice string like \"com.google.android.tts|VoiceName\".",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                AuraTextField(
                    value = voiceName,
                    onValueChange = { voiceName = it },
                    placeholder = "google or system default voice",
                    textStyle = MonoStyle,
                )
                if (voices.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Voices: " + voices.take(8).joinToString(" · "), style = MonoStyleSmall, color = mono.textSecondary)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Save voice", {
                        vm.updateSettings { it.ttsVoice = voiceName.trim() }
                        vm.showNotice("Voice saved")
                    }, modifier = Modifier.weight(1f))
                    SecondaryButton("Use Google TTS", {
                        voiceName = "google"
                        vm.updateSettings { it.ttsVoice = "google" }
                        vm.showNotice("Google TTS selected (if installed)")
                    }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                AuraTextField(
                    value = testLine,
                    onValueChange = { testLine = it },
                    placeholder = "Test sentence",
                    singleLine = false,
                    minHeight = 70.dp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InkButton("Speak", { vm.speak(testLine) }, modifier = Modifier.weight(1f))
                    SecondaryButton("Stop", { vm.stopSpeaking() }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionHeader("Shortcuts & conversation")
            SurfaceCard {
                Text(
                    "• Android assistant gesture: set AURA as assistant / use Assist to start listening.\n" +
                        "• Volume-key shortcut: double-press Volume Down (Accessibility service must be enabled).\n" +
                        "• Gemini Live conversation mode: supported via realtime session API (GeminiLiveConversationSession); requires Gemini API key/model and host UI/service integration.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            SectionHeader("Screen capture")
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MediaProjection consent", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                        Text(
                            if (permissions.screenCapture) "Granted for this session" else "Not granted",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = mono.textSecondary,
                        )
                    }
                    Badge(if (permissions.screenCapture) "on" else "off", tone = if (permissions.screenCapture) ChipTone.Live else ChipTone.Idle)
                }
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Grant screen capture", { vm.requestScreenCapture() })
                Spacer(Modifier.height(6.dp))
                Text(
                    "Consent is per session. Revoke it from the system notification or by capturing again.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
                notice?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                }
            }
        }
    }
}
