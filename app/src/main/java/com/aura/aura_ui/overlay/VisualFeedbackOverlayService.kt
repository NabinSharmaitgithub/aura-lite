package com.aura.aura_ui.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.aura.aura_ui.accessibility.AuraAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Optional edge/touch feedback used while the agent acts. Purely visual, never intercepts touch.
 */
class VisualFeedbackOverlayService : Service() {

    private var view: View? = null
    private var manager: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this) || view != null) {
            return START_NOT_STICKY
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        manager = wm
        val feedback = FeedbackView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        runCatching { wm.addView(feedback, params) }
        view = feedback

        val service = AuraAccessibilityService.instance
        service?.signals?.onEach { signal ->
            if (signal.type == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED ||
                signal.type == android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            ) {
                feedback.pulse()
            }
        }?.launchIn(scope)
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        view?.let { runCatching { manager?.removeView(it) } }
        view = null
        manager = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private class FeedbackView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = com.aura.aura_ui.ui.theme.MonoArgb.blood
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        private var alpha = 0f

        fun pulse() {
            alpha = 0.55f
            invalidate()
            postDelayed({
                alpha = 0f
                invalidate()
            }, 420)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (alpha <= 0f) return
            paint.alpha = (alpha * 255).toInt()
            val inset = 6f
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, 28f, 28f, paint)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.TRANSPARENT })
        }
    }
}
