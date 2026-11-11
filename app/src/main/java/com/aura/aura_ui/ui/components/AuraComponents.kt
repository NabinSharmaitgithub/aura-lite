package com.aura.aura_ui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aura.aura_ui.ui.theme.LocalMonoScheme
import com.aura.aura_ui.ui.theme.MonoStyle
import com.aura.aura_ui.ui.theme.ShapeTokens

// ─────────────────────────────────────────────────────────────────────────────
// Buttons
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InkButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    destructive: Boolean = false,
    fillWidth: Boolean = true,
) {
    val mono = LocalMonoScheme.current
    val haptics = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(150), label = "press")
    val bg = when {
        !enabled -> mono.ink
        destructive -> if (pressed) mono.bloodPressed else mono.blood
        else -> if (pressed) mono.inkPressed else mono.ink
    }
    Row(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 52.dp)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .semantics { role = Role.Button },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = mono.textOnInk, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = mono.textOnInk,
            maxLines = 1,
        )
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null,
) {
    val mono = LocalMonoScheme.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(150), label = "press")
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(CircleShape)
            .background(if (pressed) mono.canvas else mono.surface)
            .then(
                BorderStroke(1.dp, mono.outline).let {
                    Modifier.border(it, CircleShape)
                },
            )
            .clickable(interactionSource = source, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = mono.textPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = mono.textPrimary)
    }
}

@Composable
fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val mono = LocalMonoScheme.current
    Text(
        label,
        modifier = modifier
            .clip(ShapeTokens.Chip.let { RoundedCornerShape(it) })
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        color = color ?: mono.textSecondary,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Surfaces
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit,
) {
    val mono = LocalMonoScheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ShapeTokens.Card))
            .background(mono.surface)
            .border(1.dp, mono.outline, RoundedCornerShape(ShapeTokens.Card))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(contentPadding),
    ) { content() }
}

@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable () -> Unit,
) {
    val mono = LocalMonoScheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ShapeTokens.Card))
            .background(mono.ink)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(contentPadding),
    ) { content() }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val mono = LocalMonoScheme.current
    Text(
        text.uppercase(),
        modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        color = mono.textSecondary,
    )
}

@Composable
fun GroupedList(
    modifier: Modifier = Modifier,
    header: String? = null,
    content: @Composable () -> Unit,
) {
    val mono = LocalMonoScheme.current
    Column(modifier) {
        if (header != null) SectionHeader(header)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ShapeTokens.Card))
                .background(mono.surface)
                .border(1.dp, mono.outline, RoundedCornerShape(ShapeTokens.Card)),
        ) { content() }
    }
}

@Composable
fun GroupedRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = true,
    titleColor: Color? = null,
    isLast: Boolean = false,
) {
    val mono = LocalMonoScheme.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(mono.inkSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = mono.textOnInk, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = titleColor ?: mono.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = mono.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            } else if (showChevron && onClick != null) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = mono.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
        if (!isLast) {
            Box(
                Modifier
                    .padding(start = if (icon != null) 72.dp else 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(mono.outline),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status
// ─────────────────────────────────────────────────────────────────────────────

enum class ChipTone { Idle, Live, Broken, On }

@Composable
fun StatusChip(
    label: String,
    tone: ChipTone = ChipTone.Idle,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    count: Int? = null,
) {
    val mono = LocalMonoScheme.current
    val dot = when (tone) {
        ChipTone.Idle -> mono.textOnInkDim
        ChipTone.Live -> mono.successDot
        ChipTone.Broken -> mono.blood
        ChipTone.On -> mono.successDot
    }
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(mono.inkSoft)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dot),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = mono.textOnInk,
            maxLines = 1,
        )
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text("$count", style = MonoStyle, color = mono.textOnInkDim)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Controls
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AuraSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val mono = LocalMonoScheme.current
    val haptics = LocalHapticFeedback.current
    val track by animateColorAsState(if (checked) mono.switchOn else mono.switchOff, tween(150), label = "track")
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onCheckedChange(it)
        },
        modifier = modifier,
        enabled = enabled,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = track,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = track,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun AuraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minHeight: Dp = 52.dp,
    textStyle: TextStyle = LocalTextStyle.current,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    val mono = LocalMonoScheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(ShapeTokens.CardSmall))
            .background(mono.surfaceElevated)
            .border(1.dp, mono.outline, RoundedCornerShape(ShapeTokens.CardSmall))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = textStyle, color = mono.textSecondary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                textStyle = textStyle.copy(color = mono.textPrimary),
                singleLine = singleLine,
                cursorBrush = SolidColor(mono.ink),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun EmptyState(
    title: String,
    detail: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoScheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = mono.textPrimary,
        )
        if (detail != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                detail,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = mono.textSecondary,
            )
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            GhostButton(action, onAction, color = mono.textPrimary)
        }
    }
}

@Composable
fun PermissionBanner(
    text: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mono = LocalMonoScheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ShapeTokens.CardSmall))
            .background(mono.bloodContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = mono.blood, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = mono.blood,
        )
        GhostButton(action, onAction, color = mono.blood)
    }
}

@Composable
fun LoadState(message: String = "Loading…", modifier: Modifier = Modifier) {
    val mono = LocalMonoScheme.current
    Row(modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = mono.textSecondary)
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val mono = LocalMonoScheme.current
    Column(modifier.fillMaxWidth().padding(24.dp)) {
        Text(message, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = mono.blood)
        if (onRetry != null) GhostButton("Retry", onRetry, color = mono.textPrimary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Misc
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier) {
    val mono = LocalMonoScheme.current
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = mono.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = mono.textPrimary)
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, tone: ChipTone = ChipTone.Idle) {
    val mono = LocalMonoScheme.current
    val bg = if (tone == ChipTone.Broken) mono.bloodContainer else mono.surfaceElevated
    val fg = if (tone == ChipTone.Broken) mono.blood else mono.textSecondary
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(ShapeTokens.Chip))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        color = fg,
    )
}

/** Small hook used by the session trace and tool inventory. */
@Composable
fun Dot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val mono = LocalMonoScheme.current
    Row(modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = mono.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = mono.textSecondary)
            }
        }
        AuraSwitch(checked, onCheckedChange, enabled = enabled)
    }
}

@Composable
fun AnimatedErrorSlot(visible: Boolean, text: String, modifier: Modifier = Modifier) {
    val mono = LocalMonoScheme.current
    AnimatedVisibility(visible) {
        Text(
            text,
            modifier = modifier.fillMaxWidth().padding(top = 8.dp),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal),
            color = mono.blood,
        )
    }
}
