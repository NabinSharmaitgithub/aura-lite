package com.aura.aura_ui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyle
import com.aura.aura_ui.ui.theme.MonoStyleSmall

/**
 * Run report card with lightweight markdown-capable rendering (headings, bullets, bold-ish
 * prefixes) without pulling a heavy markdown dependency.
 */
@Composable
fun RunReportCard(
    title: String,
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoScheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(mono.surface)
            .padding(16.dp),
    ) {
        Text(title, style = MonoStyle, color = mono.textPrimary)
        Spacer(Modifier.height(8.dp))
        markdown.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(6.dp))
                line.startsWith("# ") -> Text(line.removePrefix("# "), style = MonoStyle, color = mono.textPrimary)
                line.startsWith("## ") -> Text(line.removePrefix("## "), style = MonoStyle, color = mono.textPrimary)
                line.startsWith("- ") || line.startsWith("* ") ->
                    Text("• " + line.drop(2), style = MonoStyleSmall, color = mono.textSecondary)
                line.matches(Regex("""\d+\..*""")) ->
                    Text(line, style = MonoStyleSmall, color = mono.textSecondary)
                else -> Text(line, style = MonoStyleSmall, color = mono.textSecondary)
            }
        }
    }
}
