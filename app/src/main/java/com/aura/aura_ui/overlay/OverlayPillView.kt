package com.aura.aura_ui.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import com.aura.aura_ui.state.PillState
import kotlin.math.sin

/**
 * AURA Wake Screen Overlay View — Ported directly from Figma project "aura-wake-screen" (node 0:1).
 *
 * Implements:
 * 1. Rectangle 3 (Top Card): Frosted light-grey rounded card (radius ~28dp) with JetBrains Mono
 *    ExtraBold multi-line live transcript text.
 * 2. Rectangle 1 (Bottom Pill): Black capsule pill (height ~68dp, radius ~34dp) hosting 16 vertical
 *    white capsule bars (Union elements) dynamically dancing to speech audio amplitude.
 */
class OverlayPillView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    // Paints
    private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 232, 232, 230) // bg-[#d9d9d9] opacity-47 on dark
        style = Paint.Style.FILL
    }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0A0A0A") // bg-black
        style = Paint.Style.FILL
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE // Union bars
        style = Paint.Style.FILL
    }
    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#666666")
        textSize = 11f * density
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        letterSpacing = 0.08f
    }
    private val transcriptPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111111")
        textSize = 19f * density
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#34C759")
        style = Paint.Style.FILL
    }

    // Geometry rects
    private val cardRect = RectF()
    private val pillRect = RectF()
    private val barRect = RectF()

    // State
    private var state: PillState = PillState.IDLE
    private var statusText = "Listening for your voice…"
    private var transcriptText = ""
    private var audioAmplitude = 0f // 0..1 scale

    // 16 bars base height profile matching the Figma Union layout
    private val barCount = 16
    private val baseBarHeights = floatArrayOf(
        14f, 22f, 32f, 24f, 30f, 38f, 26f, 36f,
        40f, 32f, 36f, 24f, 32f, 26f, 20f, 14f,
    )
    private val currentBarHeights = FloatArray(barCount)

    var onTapPill: (() -> Unit)? = null
    var onLongPressPill: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    // 60 FPS animation loop for smooth waveform oscillation
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            computeWaveform()
            invalidate()
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        animator.start()
    }

    fun update(state: PillState, status: String, transcript: String, amplitude: Float = 0f) {
        this.state = state
        this.statusText = status
        this.transcriptText = transcript
        this.audioAmplitude = amplitude.coerceIn(0f, 1f)
        requestLayout()
        invalidate()
    }

    private fun computeWaveform() {
        val time = SystemClock.uptimeMillis() / 1000.0
        val isListening = state == PillState.LISTENING
        val isSpeaking = state == PillState.SPEAKING

        for (i in 0 until barCount) {
            val base = baseBarHeights[i] * density
            val wave = sin(time * 6.0 + i * 0.45).toFloat() * 0.5f + 0.5f
            val target = when {
                isListening -> {
                    val boost = (audioAmplitude * 1.5f).coerceAtMost(1f)
                    base * (0.4f + wave * 0.3f + boost * 0.8f)
                }
                isSpeaking -> base * (0.5f + wave * 0.6f)
                else -> base * (0.35f + wave * 0.25f) // gentle idle breathing
            }
            // Smooth lerp
            currentBarHeights[i] = currentBarHeights[i] + (target - currentBarHeights[i]) * 0.35f
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val screenWidth = resources.displayMetrics.widthPixels
        val viewWidth = (screenWidth * 0.92f).toInt().coerceAtMost((560 * density).toInt())

        // Calculate card height dynamically based on transcript text
        val cardInnerWidth = (viewWidth - 40 * density).toInt().coerceAtLeast(100)
        val textToDisplay = if (transcriptText.isNotBlank()) transcriptText else statusText
        val staticLayout = StaticLayout.Builder.obtain(
            textToDisplay, 0, textToDisplay.length, transcriptPaint, cardInnerWidth
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f * density, 1f)
            .setIncludePad(false)
            .build()

        val headerHeight = 24 * density
        val cardContentHeight = headerHeight + staticLayout.height + 36 * density
        val cardHeight = cardContentHeight.coerceAtLeast(120 * density)
        val pillHeight = 68 * density
        val spacing = 14 * density

        val totalHeight = (cardHeight + spacing + pillHeight + 12 * density).toInt()
        setMeasuredDimension(viewWidth, totalHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val pillH = 68f * density
        val spacing = 14f * density
        val cardH = h - pillH - spacing - (8f * density)

        // 1. Draw Transcript Card (Figma Rectangle 3: rounded-[52px], bg-[#d9d9d9])
        cardRect.set(0f, 0f, w, cardH)
        val cardRadius = 28f * density
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardBgPaint)
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardBorderPaint)

        // Card Header: Green pulsing indicator + "AURA · LISTENING"
        val headerX = 22f * density
        val headerY = 24f * density
        canvas.drawCircle(headerX + 4f * density, headerY - 4f * density, 4f * density, dotPaint)
        val headerLabel = when (state) {
            PillState.LISTENING -> "AURA · LISTENING"
            PillState.SPEAKING -> "AURA · SPEAKING"
            PillState.IDLE -> "AURA · READY"
        }
        canvas.drawText(headerLabel, headerX + 16f * density, headerY, headerPaint)

        // Card Main Text: JetBrains Mono ExtraBold
        val textToDisplay = if (transcriptText.isNotBlank()) transcriptText else statusText
        val cardInnerWidth = (w - 44 * density).toInt().coerceAtLeast(100)
        val staticLayout = StaticLayout.Builder.obtain(
            textToDisplay, 0, textToDisplay.length, transcriptPaint, cardInnerWidth
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f * density, 1f)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.translate(22f * density, headerY + 16f * density)
        staticLayout.draw(canvas)
        canvas.restore()

        // 2. Draw Waveform Capsule Pill (Figma Rectangle 1: rounded-[90px], bg-black)
        val pillTop = cardH + spacing
        pillRect.set(0f, pillTop, w, pillTop + pillH)
        val pillRadius = pillH / 2f
        canvas.drawRoundRect(pillRect, pillRadius, pillRadius, pillBgPaint)

        // 3. Draw 16 Animated White Capsule Bars (Figma Union) inside the black pill
        val barW = 5.5f * density
        val barRadius = barW / 2f
        val innerPadding = 24f * density
        val usableWidth = w - innerPadding * 2f
        val step = usableWidth / (barCount - 1)
        val centerY = pillTop + pillH / 2f

        for (i in 0 until barCount) {
            val barX = innerPadding + i * step
            val barH = currentBarHeights[i].coerceIn(barW, pillH * 0.72f)
            barRect.set(barX - barRadius, centerY - barH / 2f, barX + barRadius, centerY + barH / 2f)
            canvas.drawRoundRect(barRect, barRadius, barRadius, barPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_UP -> {
                val pillTop = height - (68f * density) - (8f * density)
                if (event.y >= pillTop) {
                    onTapPill?.invoke()
                } else {
                    onDismiss?.invoke()
                }
                return true
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
