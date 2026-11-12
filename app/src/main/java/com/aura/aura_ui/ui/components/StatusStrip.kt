package com.aura.aura_ui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyleSmall

/** Persistent run status strip bound to StatusNarrator/agent run state. */
@Composable
fun StatusStrip(
    status: String,
    detail: String? = null,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoScheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(mono.surface)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.size(8.dp).clip(CircleShape).background(mono.blood),
        )
        Text(
            text = if (detail.isNullOrBlank()) status else "$status · $detail",
            style = MonoStyleSmall,
            color = mono.textSecondary,
            maxLines = 1,
        )
    }
}

/** Floating pause/resume/stop pill for the active run. */
@Composable
fun PausePill(
    paused: Boolean,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoScheme.current
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(mono.ink)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
            contentDescription = if (paused) "Resume" else "Pause",
            tint = mono.canvas,
            modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onTogglePause() },
        )
        Spacer(Modifier.size(2.dp))
        Icon(
            imageVector = Icons.Outlined.Stop,
            contentDescription = "Stop",
            tint = mono.blood,
            modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onStop() },
        )
    }
}
