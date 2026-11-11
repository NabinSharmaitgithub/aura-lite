package com.aura.aura_ui.presentation.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Icon
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.aura_ui.presentation.AuraViewModel
import com.aura.aura_ui.presentation.navigation.Routes
import com.aura.aura_ui.ui.components.AuraTextField
import com.aura.aura_ui.ui.components.Badge
import com.aura.aura_ui.ui.components.ChipTone
import com.aura.aura_ui.ui.components.EmptyState
import com.aura.aura_ui.ui.components.GhostButton
import com.aura.aura_ui.ui.components.GroupedList
import com.aura.aura_ui.ui.components.GroupedRow
import com.aura.aura_ui.ui.components.InkButton
import com.aura.aura_ui.ui.components.SecondaryButton
import com.aura.aura_ui.ui.components.SectionHeader
import com.aura.aura_ui.ui.components.SurfaceCard
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyle
import com.aura.aura_ui.ui.theme.MonoStyleSmall

@Composable
fun MemoryScreen(vm: AuraViewModel, onNavigate: (String) -> Unit) {
    val mono = LocalMonoScheme.current
    val facts by vm.facts.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var correctionsVersion by remember { mutableStateOf(0) }
    val corrections = remember(facts, correctionsVersion) { vm.corrections() }

    var newFact by remember { mutableStateOf("") }
    var factScope by remember { mutableStateOf("global") }
    var reminderText by remember { mutableStateOf("") }
    var reminderMinutes by remember { mutableStateOf("30") }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Memory", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Durable preferences and reminders. Learnings below 50% confidence are never injected.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(20.dp))
            GroupedList(header = "Settings") {
                GroupedRow(
                    title = "Extract learnings",
                    subtitle = "Only when you ask for a preference to be remembered",
                    icon = Icons.Outlined.Memory,
                    trailing = { com.aura.aura_ui.ui.components.AuraSwitch(checked = settings.learningsEnabled, onCheckedChange = { vm.updateSettings { s -> s.learningsEnabled = it } }) },
                    showChevron = false,
                )
                GroupedRow(
                    title = "Log retention",
                    subtitle = "${settings.logRetentionDays} days",
                    icon = Icons.Outlined.Notifications,
                    onClick = { onNavigate(Routes.ACTIVITY_RETENTION) },
                    isLast = true,
                )
            }
            Spacer(Modifier.height(22.dp))
        }

        item {
            SectionHeader("Learned facts · ${facts.size}")
            SurfaceCard {
                AuraTextField(
                    value = newFact,
                    onValueChange = { newFact = it },
                    placeholder = "Something AURA should remember",
                )
                Spacer(Modifier.height(8.dp))
                AuraTextField(
                    value = factScope,
                    onValueChange = { factScope = it },
                    placeholder = "Scope: global or package name",
                    textStyle = MonoStyle,
                )
                Spacer(Modifier.height(8.dp))
                InkButton("Add fact", {
                    vm.addFact(newFact, factScope.trim().ifBlank { "global" })
                    newFact = ""
                }, enabled = newFact.isNotBlank())
            }
            Spacer(Modifier.height(10.dp))
        }

        if (facts.isEmpty()) {
            item { EmptyState("Nothing learned yet", "Facts you approve will appear here, with a hard delete.") }
        } else {
            items(facts, key = { it.id }) { fact ->
                SurfaceCard(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fact.text, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = mono.textPrimary)
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Badge(fact.scope)
                                Spacer(Modifier.width(6.dp))
                                Text("${(fact.confidence * 100).toInt()}%", style = MonoStyleSmall, color = mono.textSecondary)
                            }
                        }
                        GhostButton("Delete", { vm.deleteFact(fact) }, color = mono.blood)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reminders", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = mono.textPrimary, modifier = Modifier.weight(1f))
                if (facts.isNotEmpty()) {
                    GhostButton("Clear facts", { vm.clearFacts() }, color = mono.blood)
                }
            }
            Spacer(Modifier.height(8.dp))
            SurfaceCard {
                AuraTextField(
                    value = reminderText,
                    onValueChange = { reminderText = it },
                    placeholder = "Remind me to…",
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AuraTextField(
                        value = reminderMinutes,
                        onValueChange = { reminderMinutes = it.filter { ch -> ch.isDigit() }.take(5) },
                        placeholder = "Minutes",
                        modifier = Modifier.width(120.dp),
                        textStyle = MonoStyle,
                    )
                    Spacer(Modifier.width(10.dp))
                    InkButton("Set", {
                        vm.addReminder(reminderText, reminderMinutes.toIntOrNull() ?: 30)
                        reminderText = ""
                    }, enabled = reminderText.isNotBlank())
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (reminders.isEmpty()) {
            item { EmptyState("No pending reminders", "Scheduled reminders survive a reboot.") }
        } else {
            items(reminders, key = { it.id }) { reminder ->
                SurfaceCard(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(mono.ink), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Notifications, contentDescription = null, tint = mono.textOnInk, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(reminder.text, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = mono.textPrimary)
                            Text(
                                java.text.SimpleDateFormat("d MMM · HH:mm", java.util.Locale.getDefault()).format(java.util.Date(reminder.triggerAt)),
                                style = MonoStyleSmall,
                                color = mono.textSecondary,
                            )
                        }
                        GhostButton("Cancel", { vm.deleteReminder(reminder) }, color = mono.blood)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Voice corrections", Modifier.weight(1f))
                if (corrections.isNotEmpty()) {
                    GhostButton("Clear", {
                        vm.clearCorrections()
                        correctionsVersion++
                    }, color = mono.blood)
                }
            }
            if (corrections.isEmpty()) {
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null, tint = mono.textSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("No corrections recorded yet.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
                    }
                }
            } else {
                SurfaceCard {
                    corrections.forEach { (heard, corrected) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(heard, style = MonoStyle, color = mono.textSecondary, modifier = Modifier.weight(1f))
                            Text("→", style = MonoStyle, color = mono.textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(corrected, style = MonoStyle, color = mono.textPrimary, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SurfaceCard {
                Text(
                    "Memory is local. Delete a fact and AURA tombstones the id so it cannot silently return.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = mono.textSecondary,
                )
            }
        }
    }
}
