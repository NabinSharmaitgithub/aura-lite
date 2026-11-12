package com.aura.aura_ui.presentation.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.aura_ui.presentation.AuraViewModel
import com.aura.aura_ui.presentation.formatDuration
import com.aura.aura_ui.presentation.permissions.PermissionRegistry
import com.aura.aura_ui.ui.components.KeyValueRow
import com.aura.aura_ui.ui.components.RunReportCard
import com.aura.aura_ui.ui.components.SurfaceCard
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyleSmall

@Composable
fun ReportScreen(vm: AuraViewModel) {
    val mono = LocalMonoScheme.current
    val stats by vm.reportStats.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                "On-device report",
                style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                color = mono.textPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Aggregated from the session traces stored on this phone. Nothing leaves the device.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
        }

        item {
            SurfaceCard {
                Text("Runs", style = MonoStyleSmall, color = mono.textSecondary)
                Text(
                    "${stats.runs}",
                    style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                    color = mono.textPrimary,
                )
            }
        }

        item {
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Success rate", style = MonoStyleSmall, color = mono.textSecondary)
                        Text(
                            "${(stats.successRate * 100).toInt()}%",
                            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                            color = if (stats.successRate >= 0.5f) mono.successDot else mono.blood,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Avg steps / run", style = MonoStyleSmall, color = mono.textSecondary)
                        Text(
                            String.format("%.1f", stats.avgSteps),
                            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                            color = mono.textPrimary,
                        )
                    }
                }
            }
        }

        item {
            SurfaceCard {
                KeyValueRow("Avg duration", stats.avgDurationMs.formatDuration())
                Spacer(Modifier.height(8.dp))
                KeyValueRow("Top provider", stats.topProvider)
            }
        }

        item {
            RunReportCard(
                title = "Outcome model",
                markdown = """
                    ## How runs end
                    - success: the goal was completed and verified
                    - partial: some steps completed, the rest need input
                    - failure: the provider, policy, or a step failed
                    - cancelled: you stopped or declined the run
                    - budget/time: the step or time budget was exhausted

                    ## Proof
                    Destructive/high-stakes goals only report success when a mutating action succeeded and was observed.
                """.trimIndent(),
            )
        }

        item {
            val context = LocalContext.current
            val granted = { permission: String ->
                if (permission == android.Manifest.permission.SYSTEM_ALERT_WINDOW) {
                    android.provider.Settings.canDrawOverlays(context)
                } else {
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                }
            }
            val lines = PermissionRegistry.entries.joinToString("\n") { entry ->
                val ok = granted(entry.permission)
                val state = if (ok) "granted" else if (entry.optional) "optional/missing" else "missing"
                "- ${entry.label}: $state — ${entry.purpose} (fix: ${entry.fixPath})"
            }
            RunReportCard(
                title = "Permission matrix",
                markdown = "## Feature permissions\n$lines",
            )
        }
    }
}
