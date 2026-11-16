package com.aura.aura_ui.presentation.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.aura_ui.presentation.AuraViewModel
import com.aura.aura_ui.presentation.navigation.Routes
import com.aura.aura_ui.ui.components.AuraSwitch
import com.aura.aura_ui.ui.components.AuraTextField
import com.aura.aura_ui.ui.components.Badge
import com.aura.aura_ui.ui.components.ChipTone
import com.aura.aura_ui.ui.components.GhostButton
import com.aura.aura_ui.ui.components.GroupedList
import com.aura.aura_ui.ui.components.GroupedRow
import com.aura.aura_ui.ui.components.InkButton
import com.aura.aura_ui.ui.components.SecondaryButton
import com.aura.aura_ui.ui.components.SurfaceCard
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyle
import com.aura.aura_ui.ui.theme.MonoStyleSmall

@Composable
fun ProfileScreen(
    vm: AuraViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit = {},
) {
    val mono = LocalMonoScheme.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val email by vm.firebaseEmail.collectAsStateWithLifecycle()
    val unlockSecret by vm.phoneUnlockSecret.collectAsStateWithLifecycle()
    val unlockType by vm.phoneUnlockType.collectAsStateWithLifecycle()
    val vaultItems by vm.vaultItems.collectAsStateWithLifecycle()

    var name by remember(settings.userName) { mutableStateOf(settings.userName) }
    var isEditingName by remember { mutableStateOf(false) }

    val initials = remember(name, email) {
        if (name.isNotBlank()) {
            name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
        } else if (!email.isNullOrBlank()) {
            email!!.take(2).uppercase()
        } else {
            "AU"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(mono.canvas)
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
    ) {
        item {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(mono.surface)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = mono.textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Your Profile", style = MaterialTheme.typography.titleLarge, color = mono.textPrimary)
                    Text("Account, identity & security preferences", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                }
            }
        }

        // Section 1: User Identity Card
        item {
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Avatar Box
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(mono.ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleLarge,
                            color = mono.textOnInk,
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (settings.userName.isNotBlank()) settings.userName else "AURA User",
                            style = MaterialTheme.typography.titleMedium,
                            color = mono.textPrimary,
                        )
                        Spacer(Modifier.height(4.dp))
                        if (!email.isNullOrBlank()) {
                            Text(
                                text = email!!,
                                style = MonoStyleSmall,
                                color = mono.accent,
                            )
                            Spacer(Modifier.height(4.dp))
                            Badge("Firebase Cloud Sync Active", tone = ChipTone.Live)
                        } else {
                            Text(
                                text = "Local / Guest Account",
                                style = MaterialTheme.typography.bodySmall,
                                color = mono.textSecondary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Badge("Local Only", tone = ChipTone.Idle)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (isEditingName) {
                    AuraTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Enter your display name",
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InkButton(
                            label = "Save Name",
                            onClick = {
                                vm.updateSettings { it.userName = name.trim() }
                                vm.showNotice("Name updated")
                                isEditingName = false
                            },
                            modifier = Modifier.weight(1f),
                            leading = Icons.Outlined.Check,
                        )
                        GhostButton(
                            label = "Cancel",
                            onClick = { isEditingName = false },
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            label = "Edit Name",
                            onClick = { isEditingName = true },
                            modifier = Modifier.weight(1f),
                        )
                        if (email == null) {
                            InkButton(
                                label = "Sign In / Sync",
                                onClick = { onNavigate(Routes.AUTH_LOGIN) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            GhostButton(
                                label = "Sign Out",
                                onClick = {
                                    vm.firebaseSignOut()
                                    vm.showNotice("Signed out")
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        // Section 2: Security & Vault Overview
        item {
            GroupedList(header = "Security & Phone Unlock") {
                GroupedRow(
                    title = "Security Vault",
                    subtitle = if (!unlockSecret.isNullOrBlank()) {
                        "Unlock: ${unlockType.uppercase()} · ${vaultItems.size} app credentials saved"
                    } else {
                        "${vaultItems.size} app credentials · Unlock not set"
                    },
                    icon = Icons.Outlined.Key,
                    onClick = { onNavigate(Routes.SETTINGS_VAULT) },
                )
                GroupedRow(
                    title = "Permissions Health",
                    subtitle = "Accessibility, Overlay & Notifications",
                    icon = Icons.Outlined.Security,
                    onClick = { onNavigate(Routes.SETTINGS_PERMISSIONS) },
                    isLast = true,
                )
            }

            Spacer(Modifier.height(20.dp))
        }

        // Section 3: Cloud & Storage Architecture
        item {
            GroupedList(header = "Cloud & Storage Engine") {
                GroupedRow(
                    title = "Backblaze B2 (Default Storage)",
                    subtitle = "Bucket: ${settings.b2BucketName.ifBlank { "aura-lite-storage" }} · Endpoint: ${settings.b2Endpoint}",
                    icon = Icons.Outlined.CloudUpload,
                    onClick = { onNavigate(Routes.SETTINGS_B2) },
                )
                GroupedRow(
                    title = "Firebase Cloud & Sync",
                    subtitle = if (email != null) "Connected as $email" else "Sign in to enable cross-device session backup",
                    icon = Icons.Outlined.Cloud,
                    onClick = { onNavigate(Routes.AUTH_LOGIN) },
                    isLast = true,
                )
            }

            Spacer(Modifier.height(20.dp))
        }

        // Section 4: Voice & Brain Preferences
        item {
            GroupedList(header = "Agent & Speech Engine") {
                GroupedRow(
                    title = "Speech Engine (TTS)",
                    subtitle = "sanoTTS (Ultra-light edge neural model)",
                    icon = Icons.Outlined.RecordVoiceOver,
                    onClick = { onNavigate(Routes.AGENT_VOICE) },
                )
                GroupedRow(
                    title = "Active Brain (LLM)",
                    subtitle = vm.currentEndpoint().displayName,
                    icon = Icons.Outlined.Psychology,
                    onClick = { onNavigate(Routes.AGENT_BRAIN) },
                    isLast = true,
                )
            }

            Spacer(Modifier.height(20.dp))
        }

        // Section 5: App Preferences & Dark Mode
        item {
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Dark Mode", style = MaterialTheme.typography.titleSmall, color = mono.textPrimary)
                        Text("Toggle high-contrast monochromatic theme", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                    }
                    AuraSwitch(
                        checked = settings.darkMode,
                        onCheckedChange = { vm.updateSettings { s -> s.darkMode = it } },
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-dismiss Tasks", style = MaterialTheme.typography.titleSmall, color = mono.textPrimary)
                        Text("Automatically close overlay after speaking completion/failure", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                    }
                    AuraSwitch(
                        checked = settings.autoDismissTaskOnComplete,
                        onCheckedChange = { vm.updateSettings { s -> s.autoDismissTaskOnComplete = it } },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // Section 6: Policy & Account Management
        item {
            SurfaceCard {
                Text("Privacy & Storage Guarantee", style = MaterialTheme.typography.titleSmall, color = mono.textPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    "• All user passwords, PINs, and patterns are hardware-encrypted in Android Keystore with AES-256 GCM.\n" +
                    "• Voice models (sanoTTS, Sherpa KWS) run completely offline on your device.\n" +
                    "• Files and logs are stored exclusively in your Backblaze B2 storage bucket.",
                    style = MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
            }
        }
    }
}
