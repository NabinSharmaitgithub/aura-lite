package com.aura.aura_ui.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/**
 * AURA Wake Assistant Screen — Ported directly from Figma design "aura-wake-screen" (node 0:1).
 *
 * Appears when:
 * 1. User says "Hey AURA" / "Wake up AURA"
 * 2. User presses the power button for 5 sec (Assist gesture)
 *
 * Visual hierarchy:
 * - Full-screen dimmed translucent scrim
 * - Rectangle 3 (Transcript Card): Frosted light grey card (rounded 28dp, bg-[#d9d9d9] opacity-47)
 *   with JetBrains Mono ExtraBold typography
 * - Rectangle 1 (Audio Capsule Pill): Solid black capsule (rounded 36dp) hosting 16 vertical
 *   white capsule bars (Figma Union) that oscillate dynamically to speech
 */
@Composable
fun FigmaWakeAssistantOverlay(
    visible: Boolean,
    transcript: String,
    isListening: Boolean,
    audioAmplitude: Float = 0.5f,
    onToggleListening: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(250)),
        exit = fadeOut(tween(200)),
        modifier = modifier,
    ) {
        // Dimmed translucent backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* prevent backdrop dismiss when clicking content */ },
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Rectangle 3: Transcript Card ────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFE8E8E6).copy(alpha = 0.95f))
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isListening) Color(0xFF34C759) else Color(0xFF888888)),
                                )
                                Text(
                                    text = if (isListening) "AURA · LISTENING" else "AURA · READY",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFF666666),
                                    letterSpacing = 1.sp,
                                )
                            }
                            Text(
                                text = "Power / “Hey AURA”",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF888888),
                            )
                        }

                        Text(
                            text = if (transcript.isNotBlank()) transcript else "Listening for your voice…",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            lineHeight = 26.sp,
                            color = Color(0xFF111111),
                        )
                    }
                }

                // ── Rectangle 1: Audio Waveform Capsule Pill ────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(Color(0xFF0A0A0A))
                        .clickable(onClick = onToggleListening)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FigmaWaveformBars(
                        isListening = isListening,
                        amplitude = audioAmplitude,
                    )
                }
            }
        }
    }
}

/**
 * 16 White Capsule Bars matching Figma "Union" elements in Rectangle 1.
 */
@Composable
private fun FigmaWaveformBars(
    isListening: Boolean,
    amplitude: Float,
    modifier: Modifier = Modifier,
) {
    val barCount = 16
    val baseBarHeights = remember {
        floatArrayOf(
            14f, 22f, 32f, 24f, 30f, 38f, 26f, 36f,
            40f, 32f, 36f, 24f, 32f, 26f, 20f, 14f,
        )
    }

    val transition = rememberInfiniteTransition(label = "wave_oscillation")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f, // 2*PI
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until barCount) {
            val base = baseBarHeights[i]
            val wave = sin(phase + i * 0.45f) * 0.5f + 0.5f
            val h = if (isListening) {
                val boost = (amplitude * 1.4f).coerceAtMost(1f)
                (base * (0.45f + wave * 0.35f + boost * 0.75f)).coerceIn(6f, 48f)
            } else {
                (base * (0.35f + wave * 0.25f)).coerceIn(5f, 24f)
            }

            Box(
                modifier = Modifier
                    .width(5.5.dp)
                    .height(h.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FigmaWakeAssistantOverlayPreview() {
    FigmaWakeAssistantOverlay(
        visible = true,
        transcript = "What is the weather like right now in Kathmandu?",
        isListening = true,
        audioAmplitude = 0.75f,
    )
}
