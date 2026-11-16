package com.aura.aura_ui.presentation.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.aura.aura_ui.data.prefs.VaultItem
import com.aura.aura_ui.presentation.AuraViewModel
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

@Composable
fun CredentialVaultScreen(
    vm: AuraViewModel,
    onBack: () -> Unit = {},
) {
    val mono = LocalMonoScheme.current
    val unlockType by vm.phoneUnlockType.collectAsStateWithLifecycle()
    val unlockSecret by vm.phoneUnlockSecret.collectAsStateWithLifecycle()
    val items by vm.vaultItems.collectAsStateWithLifecycle()

    var selectedType by remember(unlockType) { mutableStateOf(if (unlockType == "none") "pin" else unlockType) }
    var secretInput by remember(unlockSecret) { mutableStateOf(unlockSecret ?: "") }
    var patternSequence by remember(unlockSecret, unlockType) {
        mutableStateOf(if (unlockType == "pattern") unlockSecret ?: "" else "")
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var newService by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("App") }
    var newUsername by remember { mutableStateOf("") }
    var newSecret by remember { mutableStateOf("") }
    var newNotes by remember { mutableStateOf("") }
    var revealedIds by remember { mutableStateOf(setOf<String>()) }

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
                    Text("Security Vault", style = MaterialTheme.typography.titleLarge, color = mono.textPrimary)
                    Text("Encrypted PIN, pattern & app passkeys", style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                }
            }
        }

        // Section 1: Phone Unlock Credentials
        item {
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = mono.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Phone Unlock Credentials", style = MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                    Spacer(Modifier.weight(1f))
                    if (!unlockSecret.isNullOrBlank()) {
                        Badge("Saved (${unlockType.uppercase()})", tone = ChipTone.Live)
                    } else {
                        Badge("Not configured", tone = ChipTone.Idle)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Store your PIN or unlock pattern so AURA can wake and unlock your device when given voice commands.",
                    style = MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )

                Spacer(Modifier.height(16.dp))

                // Type selector tabs
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val types = listOf("pin" to "PIN", "pattern" to "Pattern (3x3)", "password" to "Password")
                    for ((key, label) in types) {
                        val active = selectedType == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) mono.ink else mono.surface)
                                .clickable { selectedType = key }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) mono.textOnInk else mono.textPrimary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (selectedType == "pattern") {
                    Text(
                        "Pattern Dots (1 to 9):\n1=Top-Left, 2=Top-Center, 3=Top-Right, 4=Mid-Left, 5=Center, 6=Mid-Right, 7=Bot-Left, 8=Bot-Center, 9=Bot-Right",
                        style = MonoStyleSmall,
                        color = mono.textSecondary,
                    )
                    Spacer(Modifier.height(12.dp))

                    // 3x3 interactive Pattern Selector
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        for (r in 0..2) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                for (c in 0..2) {
                                    val dot = (r * 3 + c + 1).toString()
                                    val isSelected = patternSequence.contains(dot)
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) mono.accent else mono.surface)
                                            .clickable {
                                                if (patternSequence.contains(dot)) {
                                                    // remove if tapped
                                                    patternSequence = patternSequence.replace(dot, "").replace("--", "-").trim('-')
                                                } else {
                                                    patternSequence = if (patternSequence.isBlank()) dot else "$patternSequence-$dot"
                                                }
                                                secretInput = patternSequence
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            dot,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isSelected) mono.textOnInk else mono.textPrimary,
                                        )
                                    }
                                }
                            }
                            if (r < 2) Spacer(Modifier.height(14.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    AuraTextField(
                        value = patternSequence,
                        onValueChange = {
                            patternSequence = it
                            secretInput = it
                        },
                        placeholder = "Pattern sequence e.g. 1-2-3-6-9",
                    )
                } else {
                    AuraTextField(
                        value = secretInput,
                        onValueChange = { secretInput = it },
                        placeholder = if (selectedType == "pin") "Enter PIN digits (e.g. 1234)" else "Enter screen unlock password",
                    )
                }

                Spacer(Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkButton(
                        label = "Save Unlock Key",
                        onClick = {
                            val sec = if (selectedType == "pattern") patternSequence.trim() else secretInput.trim()
                            if (sec.isNotBlank()) {
                                vm.savePhoneUnlock(selectedType, sec)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        leading = Icons.Outlined.Check,
                    )
                    if (!unlockSecret.isNullOrBlank()) {
                        GhostButton(
                            label = "Clear",
                            onClick = {
                                vm.clearPhoneUnlock()
                                secretInput = ""
                                patternSequence = ""
                            },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Test Actions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(
                        label = "Test Lock Phone",
                        onClick = { vm.testLockPhone() },
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton(
                        label = "Test Unlock Phone",
                        onClick = { vm.testUnlockPhone() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // Section 2: App Credentials & Passkeys
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Key, contentDescription = null, tint = mono.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("App & Service Credentials", style = MaterialTheme.typography.titleMedium, color = mono.textPrimary)
                Spacer(Modifier.weight(1f))
                Text("${items.size} saved", style = MonoStyleSmall, color = mono.textSecondary)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Save passwords, PINs, or passkeys for specific apps and services. AURA uses these to open locked notes, sign in to accounts, or access restricted services on command.",
                style = MaterialTheme.typography.bodySmall,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(14.dp))

            if (!showAddDialog) {
                GhostButton(
                    label = "+ Add App Credential / Passkey",
                    onClick = { showAddDialog = true },
                )
            } else {
                SurfaceCard {
                    Text("Add New Credential", style = MaterialTheme.typography.titleSmall, color = mono.textPrimary)
                    Spacer(Modifier.height(12.dp))

                    AuraTextField(
                        value = newService,
                        onValueChange = { newService = it },
                        placeholder = "App or service name (e.g. Netflix, Notes, Amazon)",
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val cats = listOf("App", "PIN", "Passkey", "Notes")
                        for (cat in cats) {
                            val active = newCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (active) mono.ink else mono.surface)
                                    .clickable { newCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    cat,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) mono.textOnInk else mono.textPrimary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    AuraTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        placeholder = "Account / Username / Email (optional)",
                    )
                    Spacer(Modifier.height(8.dp))

                    AuraTextField(
                        value = newSecret,
                        onValueChange = { newSecret = it },
                        placeholder = "Password, PIN, or Passkey secret",
                    )
                    Spacer(Modifier.height(8.dp))

                    AuraTextField(
                        value = newNotes,
                        onValueChange = { newNotes = it },
                        placeholder = "Notes / instructions for AURA (optional)",
                    )
                    Spacer(Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InkButton(
                            label = "Save Credential",
                            onClick = {
                                if (newService.isNotBlank() && newSecret.isNotBlank()) {
                                    vm.saveVaultItem(
                                        VaultItem(
                                            service = newService.trim(),
                                            category = newCategory,
                                            username = newUsername.trim(),
                                            secret = newSecret.trim(),
                                            notes = newNotes.trim(),
                                        )
                                    )
                                    newService = ""
                                    newUsername = ""
                                    newSecret = ""
                                    newNotes = ""
                                    showAddDialog = false
                                }
                            },
                            enabled = newService.isNotBlank() && newSecret.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            leading = Icons.Outlined.Check,
                        )
                        GhostButton(
                            label = "Cancel",
                            onClick = { showAddDialog = false },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // List of stored vault items
        if (items.isEmpty()) {
            item {
                SurfaceCard {
                    Text(
                        "No app credentials stored yet.\nTap '+ Add App Credential' to add passkeys or passwords for apps AURA should handle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = mono.textSecondary,
                    )
                }
            }
        } else {
            items(items, key = { it.id }) { item ->
                val isRevealed = revealedIds.contains(item.id)
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.service, style = MaterialTheme.typography.titleSmall, color = mono.textPrimary)
                                Spacer(Modifier.width(8.dp))
                                Badge(item.category, tone = ChipTone.Idle)
                            }
                            if (item.username.isNotBlank()) {
                                Text("User: ${item.username}", style = MonoStyleSmall, color = mono.textSecondary)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (isRevealed) item.secret else "••••••••••••",
                                style = MonoStyle,
                                color = if (isRevealed) mono.accent else mono.textSecondary,
                            )
                            if (item.notes.isNotBlank()) {
                                Text(item.notes, style = MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                            }
                        }

                        IconButton(onClick = {
                            revealedIds = if (isRevealed) revealedIds - item.id else revealedIds + item.id
                        }) {
                            Icon(
                                if (isRevealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (isRevealed) "Hide" else "Reveal",
                                tint = mono.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }

                        IconButton(onClick = { vm.deleteVaultItem(item.id) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete",
                                tint = mono.accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = mono.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Hardware Security Architecture", style = MaterialTheme.typography.titleSmall, color = mono.textPrimary)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "All credentials, PINs, and patterns are encrypted in Android Keystore with AES-256 GCM authenticated encryption. Secrets are decrypted only in memory when executing actions and never uploaded to cloud backups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
            }
        }
    }
}
