package com.aura.aura_ui.presentation.screens

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
import androidx.compose.foundation.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aura.aura_ui.presentation.AuraViewModel
import com.aura.aura_ui.presentation.formatDuration
import com.aura.aura_ui.ui.components.KeyValueRow
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
    }
}
