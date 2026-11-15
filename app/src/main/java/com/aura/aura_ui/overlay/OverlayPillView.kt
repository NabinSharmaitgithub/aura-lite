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
import com.aura.aura_ui.accessibility.ScreenGeometry
import com.aura.aura_ui.state.PillState
import kotlin.math.sin

/**
 * AURA Wake Screen Overlay View — Ported directly from Figma project "aura-wake-screen" (node 0:1).
 *
 * Implements:
 * 1. Rectangle 3 (Top Card): Frosted light-grey rounded card (radius ~28dp) with JetBrains Mono
 *    ExtraBold multi-line live transcript and status text.
 *    - Alternates smoothly between:
 *      (a) User Command (the initial goal/instruction)
 *      (b) Live Caption (current action / tool / thought being executed)
 *      (c) To-do List (plan checklist with done [✓], in-progress [•], and pending [ ] steps)
 * 2. Rectangle 1 (Bottom Pill): Black capsule pill (height ~68dp, radius ~34dp) hosting 16 vertical
 *    white capsule bars (Union elements) dynamically dancing to speech audio amplitude.
 */
class OverlayPillView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    // Modes for cycling display in Rectangle 3 (Top Card)
    enum class AlternateMode {
        COMMAND,
        CAPTION,
        TODO,
    }

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
    private val mainTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#111111")
        textSize = 18f * density
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val todoTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        textSize = 14f * density
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#34C759") // Green
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

    // Alternate cycling state
    private var currentMode = AlternateMode.COMMAND
    private var lastModeSwitchTime = 0L
    private val MODE_CYCLE_INTERVAL_MS = 3500L // 3.5 seconds per view

    private var userCommand: String = ""
    private var liveCaption: String = ""
    private var todoList: List<String> = emptyList()
    private var isAgentRunning: Boolean = false
    private var completionAnswer: String? = null
    private var pendingQuestion: String? = null

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

    // 60 FPS animation loop for smooth waveform oscillation & mode cycling
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            computeWaveform()
            checkModeCycling()
            invalidate()
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        lastModeSwitchTime = SystemClock.uptimeMillis()
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

    fun setTaskData(command: String, isRunning: Boolean) {
        if (command.isNotBlank()) {
            this.userCommand = command
        }
        this.isAgentRunning = isRunning
        if (isRunning) {
            this.completionAnswer = null
            this.pendingQuestion = null
        }
        requestLayout()
        invalidate()
    }

    fun updateLiveCaption(caption: String) {
        if (caption.isNotBlank()) {
            this.liveCaption = caption
            this.statusText = caption
            // If we get a fresh caption while running, switch to caption mode briefly to highlight it
            if (isAgentRunning && state != PillState.LISTENING) {
                currentMode = AlternateMode.CAPTION
                lastModeSwitchTime = SystemClock.uptimeMillis()
            }
            requestLayout()
            invalidate()
        }
    }

    fun updateTodoList(steps: List<String>) {
        this.todoList = steps
        requestLayout()
        invalidate()
    }

    fun setCompletion(answer: String?) {
        this.completionAnswer = answer
        this.isAgentRunning = false
        requestLayout()
        invalidate()
    }

    fun setPendingQuestion(question: String?) {
        this.pendingQuestion = question
        requestLayout()
        invalidate()
    }

    private fun checkModeCycling() {
        if (!isAgentRunning) return
        // Do not alternate while the user is actively speaking or hearing AURA reply
        if (state == PillState.LISTENING || state == PillState.SPEAKING) return
        if (pendingQuestion != null || completionAnswer != null) return

        val now = SystemClock.uptimeMillis()
        if (now - lastModeSwitchTime >= MODE_CYCLE_INTERVAL_MS) {
            lastModeSwitchTime = now
            val hasCommand = userCommand.isNotBlank()
            val hasCaption = liveCaption.isNotBlank()
            val hasTodo = todoList.isNotEmpty()

            currentMode = when (currentMode) {
                AlternateMode.COMMAND -> {
                    if (hasCaption) AlternateMode.CAPTION
                    else if (hasTodo) AlternateMode.TODO
                    else AlternateMode.COMMAND
                }
                AlternateMode.CAPTION -> {
                    if (hasTodo) AlternateMode.TODO
                    else if (hasCommand) AlternateMode.COMMAND
                    else AlternateMode.CAPTION
                }
                AlternateMode.TODO -> {
                    if (hasCommand) AlternateMode.COMMAND
                    else if (hasCaption) AlternateMode.CAPTION
                    else AlternateMode.TODO
                }
            }
            requestLayout()
        }
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
                    val boost = (audioAmplitude * 1.6f).coerceAtMost(1f)
                    base * (0.4f + wave * 0.3f + boost * 0.85f)
                }
                isSpeaking -> base * (0.5f + wave * 0.6f)
                isAgentRunning -> base * (0.45f + wave * 0.35f) // energetic pulse while working
                else -> base * (0.35f + wave * 0.25f) // gentle idle breathing
            }
            // Smooth lerp
            currentBarHeights[i] = currentBarHeights[i] + (target - currentBarHeights[i]) * 0.35f
        }
    }

    private fun getDisplayTextAndHeader(): Pair<String, Pair<String, Int>> {
        val green = Color.parseColor("#34C759")
        val cyan = Color.parseColor("#00E5FF")
        val purple = Color.parseColor("#AF52DE")
        val orange = Color.parseColor("#FF9500")
        val blue = Color.parseColor("#007AFF")

        // 1. Listening / User speaking
        if (state == PillState.LISTENING) {
            val text = if (transcriptText.isNotBlank()) transcriptText else "Listening for your instruction…"
            return text to ("AURA · LISTENING" to green)
        }

        // 2. Pending Question (HITL ask_user)
        if (pendingQuestion != null) {
            return pendingQuestion!! to ("AURA · ASKS YOU (Tap to answer)" to orange)
        }

        // 3. Completion
        if (completionAnswer != null) {
            return completionAnswer!! to ("AURA · TASK COMPLETE" to green)
        }

        // 4. Agent Running — Alternate Cycling
        if (isAgentRunning) {
            return when (currentMode) {
                AlternateMode.COMMAND -> {
                    val text = if (userCommand.isNotBlank()) "\"$userCommand\"" else statusText
                    text to ("AURA · USER COMMAND" to cyan)
                }
                AlternateMode.CAPTION -> {
                    val text = if (liveCaption.isNotBlank()) liveCaption else "Working on your task…"
                    text to ("AURA · LIVE ACTION" to green)
                }
                AlternateMode.TODO -> {
                    val text = if (todoList.isNotEmpty()) {
                        todoList.take(5).joinToString("\n")
                    } else if (liveCaption.isNotBlank()) {
                        liveCaption
                    } else {
                        "Executing steps…"
                    }
                    text to ("AURA · TO-DO LIST" to purple)
                }
            }
        }

        // 5. Speaking
        if (state == PillState.SPEAKING) {
            val text = if (transcriptText.isNotBlank()) transcriptText else "Speaking…"
            return text to ("AURA · SPEAKING" to blue)
        }

        // 6. Idle
        val text = if (transcriptText.isNotBlank()) transcriptText else "Tap the bar to speak"
        return text to ("AURA · READY" to green)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val (screenWidth, _) = ScreenGeometry.realSizePx(context)
        val viewWidth = (screenWidth * 0.92f).toInt().coerceAtMost((560 * density).toInt())

        val (displayText, _) = getDisplayTextAndHeader()
        val paintToUse = if (isAgentRunning && currentMode == AlternateMode.TODO && todoList.isNotEmpty()) {
            todoTextPaint
        } else {
            mainTextPaint
        }

        val cardInnerWidth = (viewWidth - 44 * density).toInt().coerceAtLeast(100)
        val staticLayout = StaticLayout.Builder.obtain(
            displayText, 0, displayText.length, paintToUse, cardInnerWidth
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(3f * density, 1f)
            .setIncludePad(false)
            .build()

        val headerHeight = 24 * density
        val cardContentHeight = headerHeight + staticLayout.height + 34 * density
        val cardHeight = cardContentHeight.coerceIn(110 * density, 240 * density)
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

        // 1. Draw Card (Figma Rectangle 3: rounded-[28dp], bg-[#d9d9d9] frosted)
        cardRect.set(0f, 0f, w, cardH)
        val cardRadius = 28f * density
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardBgPaint)
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardBorderPaint)

        // Card Header: Status Dot + Header Tag
        val (displayText, headerInfo) = getDisplayTextAndHeader()
        val (headerLabel, dotColor) = headerInfo
        dotPaint.color = dotColor

        val headerX = 22f * density
        val headerY = 24f * density
        canvas.drawCircle(headerX + 4f * density, headerY - 4f * density, 4f * density, dotPaint)
        canvas.drawText(headerLabel, headerX + 16f * density, headerY, headerPaint)

        // Card Main Content Text: JetBrains Mono ExtraBold
        val paintToUse = if (isAgentRunning && currentMode == AlternateMode.TODO && todoList.isNotEmpty()) {
            todoTextPaint
        } else {
            mainTextPaint
        }

        val cardInnerWidth = (w - 44 * density).toInt().coerceAtLeast(100)
        val staticLayout = StaticLayout.Builder.obtain(
            displayText, 0, displayText.length, paintToUse, cardInnerWidth
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(3f * density, 1f)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.translate(22f * density, headerY + 14f * density)
        staticLayout.draw(canvas)
        canvas.restore()

        // 2. Draw Waveform Capsule Pill (Figma Rectangle 1: rounded-[34dp], bg-black)
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
