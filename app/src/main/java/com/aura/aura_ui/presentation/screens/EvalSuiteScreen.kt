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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.aura_ui.data.eval.EvalTask
import com.aura.aura_ui.presentation.AuraViewModel
import com.aura.aura_ui.ui.components.Badge
import com.aura.aura_ui.ui.components.ChipTone
import com.aura.aura_ui.ui.components.EmptyState
import com.aura.aura_ui.ui.components.GhostButton
import com.aura.aura_ui.ui.components.SurfaceCard
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyle
import com.aura.aura_ui.ui.theme.MonoStyleSmall

@Composable
fun EvalSuiteScreen(vm: AuraViewModel) {
    val mono = LocalMonoScheme.current
    val tasks = remember { vm.evalTasks() }
    val problems = remember { vm.validateEval(tasks) }
    val agent by vm.agent.collectAsStateWithLifecycle()
    var confirmTask by remember { mutableStateOf<EvalTask?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
    ) {
        item {
            Text("Eval suite", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = mono.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "A 20-task quality harness. The list is data from assets/eval/tasks.json. Running a task starts a normal agent run with the usual confirmations.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
            Spacer(Modifier.height(16.dp))
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Catalogue check", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary, modifier = Modifier.weight(1f))
                    Badge(
                        if (problems.isEmpty()) "ok" else "${problems.size} issues",
                        tone = if (problems.isEmpty()) ChipTone.Live else ChipTone.Broken,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (problems.isEmpty()) "Every expected tool exists in the live catalogue."
                    else problems.joinToString("\n"),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = if (problems.isEmpty()) mono.textSecondary else mono.blood,
                )
                if (agent.running) {
                    Spacer(Modifier.height(8.dp))
                    Text("A run is active: ${agent.goal.take(80)}", style = MonoStyleSmall, color = mono.textSecondary)
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (tasks.isEmpty()) {
            item { EmptyState("No tasks", "Add assets/eval/tasks.json to ship the suite.") }
        }

        items(tasks, key = { it.id }) { task ->
            SurfaceCard(modifier = Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(task.id, style = MonoStyle, color = mono.textPrimary, modifier = Modifier.weight(1f))
                    Badge(task.domain)
                    if (task.destructive) {
                        Spacer(Modifier.width(4.dp))
                        Badge("confirm", tone = ChipTone.Broken)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(task.goal, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = mono.textPrimary)
                if (task.expectedTools.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("expects: ${task.expectedTools.joinToString(", ")}", style = MonoStyleSmall, color = mono.textSecondary)
                }
                if (task.forbiddenTools.isNotEmpty()) {
                    Text("forbids: ${task.forbiddenTools.joinToString(", ")}", style = MonoStyleSmall, color = mono.blood)
                }
                if (task.note != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(task.note, style = MonoStyleSmall, color = mono.textSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Run task", {
                        if (task.destructive) confirmTask = task else vm.runEvalTask(task)
                    }, color = mono.textPrimary)
                    Text("max ${task.maxSteps} steps", style = MonoStyleSmall, color = mono.textSecondary, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        }
    }

    val pending = confirmTask
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirmTask = null },
            title = { Text("Run destructive eval task?") },
            text = { Text("${pending.goal}\n\nThis task can send or change something. You will still see the normal confirmation card.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmTask = null
                    vm.runEvalTask(pending)
                }) { Text("Run") }
            },
            dismissButton = { TextButton(onClick = { confirmTask = null }) { Text("Cancel") } },
        )
    }
}
