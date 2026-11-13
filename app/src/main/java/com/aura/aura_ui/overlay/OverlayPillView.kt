package com.aura.aura_ui.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.aura.aura_ui.state.PillState
import com.aura.aura_ui.ui.theme.MonoArgb

/**
 * The floating assistant pill. Ink surface, blood dot while listening, transcript while the
 * recognizer is producing partials. One tap starts or cancels; long-press closes the overlay.
 */
class OverlayPillView(context: Context) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MonoArgb.ink }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = MonoArgb.hairlineOnInk
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MonoArgb.textOnInkDim }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MonoArgb.textOnInk
        textSize = 34f
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MonoArgb.textOnInkDim
        textSize = 26f
    }
    private val rect = RectF()

    private var state: PillState = PillState.IDLE
    private var status = "Tap to talk"
    private var transcript = ""

    var onTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null

    init {
        setOnClickListener { onTap?.invoke() }
        setOnLongClickListener {
            onLongPress?.invoke()
            true
        }
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun update(state: PillState, status: String, transcript: String) {
        this.state = state
        this.status = status
        this.transcript = transcript
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (resources.displayMetrics.widthPixels * 0.86f).toInt().coerceAtMost(900)
        val height = (112 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = 2f
        rect.set(inset, inset, width - inset, height - inset)
        val radius = rect.height() / 2f
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.drawRoundRect(rect, radius, radius, border)

        val centerY = height / 2f
        val dotX = 42f * resources.displayMetrics.density
        dot.color = when (state) {
            PillState.LISTENING -> MonoArgb.blood
            PillState.SPEAKING -> MonoArgb.textOnInk
            PillState.IDLE -> MonoArgb.textOnInkDim
        }
        canvas.drawCircle(dotX, centerY - 10f, 7f * resources.displayMetrics.density, dot)

        val contentX = dotX + 26f * resources.displayMetrics.density
        textPaint.color = MonoArgb.textOnInk
        canvas.drawText(status, contentX, centerY + 2f, textPaint)
        if (transcript.isNotBlank()) {
            val clipped = transcript.take(46)
            dimPaint.color = MonoArgb.textOnInkDim
            canvas.drawText(clipped, contentX, centerY + 30f, dimPaint)
        }
    }
}
