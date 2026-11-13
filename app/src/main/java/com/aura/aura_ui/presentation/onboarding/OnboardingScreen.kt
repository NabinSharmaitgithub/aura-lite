package com.aura.aura_ui.presentation.onboarding

import android.Manifest
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.aura.aura_ui.state.OnboardingStep
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.aura_ui.presentation.AuraViewModel
import com.aura.aura_ui.presentation.openIntent
import com.aura.aura_ui.presentation.openPackageSettings
import com.aura.aura_ui.presentation.rememberPermissions
import com.aura.aura_ui.ui.components.AuraSwitch
import com.aura.aura_ui.ui.components.AuraTextField
import com.aura.aura_ui.ui.components.Badge
import com.aura.aura_ui.ui.components.ChipTone
import com.aura.aura_ui.ui.components.GhostButton
import com.aura.aura_ui.ui.components.InkButton
import com.aura.aura_ui.ui.components.SecondaryButton
import com.aura.aura_ui.ui.components.SurfaceCard
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyle
import com.aura.aura_ui.ui.theme.MonoStyleSmall
import kotlinx.coroutines.delay

private const val LAST_STEP = 9

@Composable
fun OnboardingScreen(vm: AuraViewModel, onFinished: () -> Unit) {
    val mono = LocalMonoScheme.current
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val permissions = rememberPermissions(vm)
    val verification by vm.verification.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val step = OnboardingStep.fromIndex(settings.onboardingStep.coerceIn(0, OnboardingStep.DONE.index))

    var consent by remember { mutableStateOf(false) }
    var name by remember(settings.userName) { mutableStateOf(settings.userName) }
    var key by remember { mutableStateOf("") }

    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    fun go(next: Int) = vm.setOnboardingStep(next.coerceIn(0, OnboardingStep.DONE.index))

    // Permission steps advance themselves the moment the live check flips.
    LaunchedEffect(step, permissions, settings.wakeWordEnabled) {
        val granted = when (step) {
            OnboardingStep.ACCESSIBILITY -> permissions.accessibility
            OnboardingStep.OVERLAY -> permissions.overlay
            OnboardingStep.NOTIFICATIONS -> permissions.notificationListener
            OnboardingStep.MICROPHONE -> permissions.microphone && permissions.notifications
            else -> false
        }
        if (granted) {
            delay(550)
            go(step.next().index)
        }
    }

    Column(Modifier.fillMaxSize().background(mono.canvas).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step != OnboardingStep.WELCOME) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).clickable { go(step.previous().index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = mono.textPrimary, modifier = Modifier.size(20.dp))
                }
            } else {
                Spacer(Modifier.size(40.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(OnboardingStep.DONE.index + 1) { index ->
                    Box(
                        Modifier
                            .size(width = if (index == step.index) 18.dp else 6.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(if (index <= step.index) mono.ink else mono.outline),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text("${step.index + 1}/10", style = MonoStyleSmall, color = mono.textSecondary)
            Spacer(Modifier.size(40.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            when (step) {
                OnboardingStep.WELCOME -> {
                    Spacer(Modifier.height(40.dp))
                    Text("AURA", style = androidx.compose.material3.MaterialTheme.typography.displayLarge, color = mono.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "An agent that actually operates your phone.",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                        color = mono.textPrimary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Ask in your own words. AURA plans, taps, types, verifies and shows you exactly what it did. No account. No cloud. Your keys stay encrypted on this device.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        color = mono.textSecondary,
                    )
                    Spacer(Modifier.height(28.dp))
                    SurfaceCard {
                        OnboardingBullet(Icons.Outlined.Shield, "You are in control", "Pause immediately. Destructive goals need a confirmation.")
                        OnboardingBullet(Icons.Outlined.Lock, "On-device by default", "Traces, memory and keys never touch an AURA server.")
                        OnboardingBullet(Icons.Outlined.Layers, "One tool contract", "The in-app agent and paired clients use the same tools.")
                    }
                }

                OnboardingStep.DEAL -> {
                    Spacer(Modifier.height(24.dp))
                    StepTitle("How AURA works", "Read the deal before you start.")
                    Spacer(Modifier.height(16.dp))
                    SurfaceCard {
                        PolicyLine("AURA reads the accessibility tree and acts only when you submit a goal.")
                        PolicyLine("Every mutating action is followed by a verification read.")
                        PolicyLine("Payments, sends and deletes stop for explicit confirmation.")
                        PolicyLine("A blocked app is off limits. A missing permission returns a fix path, not a silent failure.")
                        PolicyLine("You can pause or stop any run from the notification, the overlay, or Home.")
                    }
                    Spacer(Modifier.height(16.dp))
                    SurfaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("I understand and consent", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                                Text(
                                    "AURA will operate apps on this device when asked.",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = mono.textSecondary,
                                )
                            }
                            AuraSwitch(checked = consent, onCheckedChange = { consent = it })
                        }
                    }
                }

                OnboardingStep.NAME -> {
                    Spacer(Modifier.height(24.dp))
                    StepTitle("Profile and provider", "Your name, and the model key that powers AURA.")
                    Spacer(Modifier.height(16.dp))
                    SurfaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Person, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("What should AURA call you?", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                        }
                        Spacer(Modifier.height(10.dp))
                        AuraTextField(value = name, onValueChange = { name = it }, placeholder = "First name")
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton("Save name", { vm.updateSettings { it.userName = name.trim() } })
                    }
                    Spacer(Modifier.height(14.dp))
                    SurfaceCard {
                        Text("Provider", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(vm.endpoints, key = { it.id }) { endpoint ->
                                val selected = endpoint.id == settings.llmEndpointId
                                Box(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(if (selected) mono.ink else mono.surfaceElevated)
                                        .clickable { vm.selectEndpoint(endpoint.id) }
                                        .padding(horizontal = 13.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        endpoint.displayName,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                        color = if (selected) mono.textOnInk else mono.textPrimary,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val endpoint = vm.currentEndpoint()
                        Text(
                            "Key hint: ${endpoint.keyHint}",
                            style = MonoStyleSmall,
                            color = mono.textSecondary,
                        )
                        Spacer(Modifier.height(8.dp))
                        AuraTextField(value = key, onValueChange = { key = it }, placeholder = "Paste your key", textStyle = MonoStyle)
                        Spacer(Modifier.height(8.dp))
                        InkButton("Save key", {
                            vm.saveProviderKey(endpoint, key)
                            key = ""
                        }, enabled = key.isNotBlank())
                    }
                }

                OnboardingStep.BRAIN -> {
                    Spacer(Modifier.height(24.dp))
                    StepTitle("A note on Android 13+", "Sideloaded apps may need one extra tap.")
                    Spacer(Modifier.height(16.dp))
                    SurfaceCard {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Warning, contentDescription = null, tint = mono.blood, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "If Android blocks the accessibility toggle, open App info for AURA, tap the three-dot menu, choose “Allow restricted settings”, then return here. This is a platform guard against sideloaded apps, not an AURA permission.",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                color = mono.textSecondary,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton("Open AURA app info", {
                            context.openPackageSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        })
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("This step is optional and can be skipped.", style = MonoStyleSmall, color = mono.textSecondary)
                }

                4 -> PermissionStep(
                    icon = Icons.Outlined.AccessibilityNew,
                    title = "Accessibility service",
                    body = "This is how AURA reads the screen and taps, types and scrolls. Without it, every UI tool returns permission_denied.",
                    granted = permissions.accessibility,
                    action = "Open accessibility settings",
                    onAction = { context.openIntent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                    footnote = "AURA never reads passwords. It stops when you pause.",
                )

                5 -> PermissionStep(
                    icon = Icons.Outlined.Layers,
                    title = "Display over other apps",
                    body = "The companion pill and live trace float above the current app so you can pause without switching back.",
                    granted = permissions.overlay,
                    action = "Allow overlay",
                    onAction = { context.openPackageSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION) },
                    footnote = "You can revoke this later in system settings.",
                )

                6 -> PermissionStep(
                    icon = Icons.Outlined.Notifications,
                    title = "Notification access",
                    body = "AURA can read and dismiss notifications only when you ask it to. This is a standard Android listener, not a cloud service.",
                    granted = permissions.notificationListener,
                    action = "Open notification access",
                    onAction = { context.openIntent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                    footnote = "Skip if you never want notification tools.",
                )

                OnboardingStep.MICROPHONE -> {
                    Spacer(Modifier.height(24.dp))
                    StepTitle("Microphone and notifications", "Voice and run status.")
                    Spacer(Modifier.height(16.dp))
                    SurfaceCard {
                        PermissionStatusLine("Microphone", permissions.microphone)
                        Spacer(Modifier.height(10.dp))
                        PermissionStatusLine("Notifications", permissions.notifications)
                        Spacer(Modifier.height(14.dp))
                        InkButton("Grant both", {
                            val permissionsToAsk = buildList {
                                add(Manifest.permission.RECORD_AUDIO)
                                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            runtimeLauncher.launch(permissionsToAsk.toTypedArray())
                        }, enabled = !(permissions.microphone && permissions.notifications))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Both are optional. Text input works without the microphone.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = mono.textSecondary,
                        )
                    }
                }

                OnboardingStep.VOICE -> {
                    Spacer(Modifier.height(24.dp))
                    StepTitle("Verify the key", "One live call to the provider you selected.")
                    Spacer(Modifier.height(16.dp))
                    val endpoint = vm.currentEndpoint()
                    SurfaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(endpoint.displayName, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                                Text(
                                    if (vm.hasKey(endpoint)) "Key stored locally" else "No key saved",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = mono.textSecondary,
                                )
                            }
                            Badge(if (vm.hasKey(endpoint)) "saved" else "missing", tone = if (vm.hasKey(endpoint)) ChipTone.Live else ChipTone.Broken)
                        }
                        Spacer(Modifier.height(12.dp))
                        AuraTextField(value = key, onValueChange = { key = it }, placeholder = "Paste key to replace", textStyle = MonoStyle)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SecondaryButton("Save", {
                                vm.saveProviderKey(endpoint, key)
                                key = ""
                            }, modifier = Modifier.weight(1f), enabled = key.isNotBlank())
                            InkButton("Verify", {
                                vm.verifyProviderKey(endpoint, key)
                            }, modifier = Modifier.weight(1f))
                        }
                        verification?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                it.message,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = if (it.ok) mono.successDot else mono.blood,
                            )
                        }
                    }
                    if (!verification?.ok.orFalse()) {
                        Spacer(Modifier.height(10.dp))
                        Text("You cannot continue until a live verification succeeds.", style = MonoStyleSmall, color = mono.textSecondary)
                    }
                }

                OnboardingStep.DONE -> {
                    Spacer(Modifier.height(24.dp))
                    StepTitle("Ready", "AURA stays out of the way until you ask.")
                    Spacer(Modifier.height(16.dp))
                    SurfaceCard {
                        ReadyLine("Accessibility", permissions.accessibility)
                        ReadyLine("Overlay", permissions.overlay)
                        ReadyLine("Notification access", permissions.notificationListener)
                        ReadyLine("Microphone", permissions.microphone)
                        ReadyLine("Notifications", permissions.notifications)
                        ReadyLine("Provider key verified", verification?.ok == true)
                    }
                    Spacer(Modifier.height(14.dp))
                    SurfaceCard {
                        Text(
                            "Start with something small: “What is on my screen?” or “Turn on Do Not Disturb”. Every step is recorded in Activity.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = mono.textSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            notice?.let {
                Text(it, style = MonoStyleSmall, color = mono.textSecondary)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            val canAdvance = when (step) {
                OnboardingStep.DEAL -> consent
                OnboardingStep.NAME -> settings.userName.isNotBlank() && vm.hasKey(vm.currentEndpoint())
                OnboardingStep.VOICE -> verification?.ok == true
                else -> true
            }
            InkButton(
                label = if (step == OnboardingStep.DONE) "Enter AURA" else "Continue",
                onClick = {
                    if (step == OnboardingStep.DONE) {
                        vm.completeOnboarding()
                        onFinished()
                    } else {
                        go(step.next().index)
                    }
                },
                enabled = canAdvance,
                leading = if (step == OnboardingStep.DONE) Icons.Outlined.Check else Icons.Outlined.ArrowForward,
            )
            if (step.index in 3..7) {
                Spacer(Modifier.height(8.dp))
                GhostButton("Skip for now", { go(step + 1) }, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    val mono = LocalMonoScheme.current
    Text(title, style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = mono.textSecondary)
}

@Composable
private fun OnboardingBullet(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    val mono = LocalMonoScheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
            Text(body, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
        }
    }
}

@Composable
private fun PolicyLine(text: String) {
    val mono = LocalMonoScheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(mono.ink))
        Spacer(Modifier.width(10.dp))
        Text(text, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
    }
}

@Composable
private fun PermissionStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    granted: Boolean,
    action: String,
    onAction: () -> Unit,
    footnote: String,
) {
    val mono = LocalMonoScheme.current
    Spacer(Modifier.height(24.dp))
    StepTitle(title, if (granted) "Granted." else "Grant this to continue, or skip.")
    Spacer(Modifier.height(18.dp))
    SurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary, modifier = Modifier.weight(1f))
            Badge(if (granted) "on" else "off", tone = if (granted) ChipTone.Live else ChipTone.Broken)
        }
        Spacer(Modifier.height(10.dp))
        Text(body, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = mono.textSecondary)
        Spacer(Modifier.height(14.dp))
        if (!granted) {
            InkButton(action, onAction)
        } else {
            Text("AURA will move on automatically.", style = MonoStyleSmall, color = mono.textSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Text(footnote, style = MonoStyleSmall, color = mono.textSecondary)
    }
}

@Composable
private fun PermissionStatusLine(label: String, granted: Boolean) {
    val mono = LocalMonoScheme.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = mono.textPrimary, modifier = Modifier.weight(1f))
        Badge(if (granted) "granted" else "not granted", tone = if (granted) ChipTone.Live else ChipTone.Broken)
    }
}

@Composable
private fun ReadyLine(label: String, ok: Boolean) {
    val mono = LocalMonoScheme.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = mono.textPrimary, modifier = Modifier.weight(1f))
        Badge(if (ok) "ready" else "optional", tone = if (ok) ChipTone.Live else ChipTone.Idle)
    }
}

private fun Boolean?.orFalse(): Boolean = this == true
