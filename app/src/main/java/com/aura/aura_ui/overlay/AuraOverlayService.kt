package com.aura.aura_ui.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import com.aura.aura_ui.notifications.AuraNotifications
import com.aura.aura_ui.services.AssistantForegroundService
import com.aura.aura_ui.state.PillState
import com.aura.aura_ui.voice.VoiceEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Floating assistant surface — Ported directly from Figma project "aura-wake-screen" (node 0:1).
 *
 * Triggered when:
 * 1. User says "Hey AURA" / "Wake up AURA" (via WakeWordListeningService)
 * 2. User presses power button for 5 sec or home long-press (via AssistActivity / ACTION_ASSIST)
 * 3. User taps the Assistant shortcut chip in the app UI
 *
 * Displays:
 * - Rectangle 3 (Top Card): Frosted light grey card with JetBrains Mono ExtraBold live transcript
 * - Rectangle 1 (Bottom Pill): Black capsule pill with 16 animated white bars (Union) oscillating to speech
 */
@AndroidEntryPoint
class AuraOverlayService : Service() {

    @Inject lateinit var voice: VoiceEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var view: OverlayPillView? = null

    override fun onCreate() {
        super.onCreate()
        AuraNotifications.ensureChannels(this)
        startForeground(AuraNotifications.ID_RUN, AuraNotifications.wakeWordNotification(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show overlay — SYSTEM_ALERT_WINDOW permission not granted")
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_HIDE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_AND_LISTEN -> {
                showFigmaOverlay()
                AssistantForegroundService.listen(this)
            }
            else -> {
                showFigmaOverlay()
            }
        }

        observeVoice()
        return START_STICKY
    }

    private fun showFigmaOverlay() {
        if (view != null) return

        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = manager

        val pill = OverlayPillView(this)
        pill.onTapPill = {
            if (voice.listening.value) {
                voice.cancelListening()
                pill.update(pillState(), "Tap to talk", "")
            } else {
                AssistantForegroundService.listen(this)
            }
        }
        pill.onLongPressPill = { stopSelf() }
        pill.onDismiss = { stopSelf() }

        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (24 * density).toInt()
        }

        runCatching { manager.addView(pill, params) }
        view = pill
    }

    private fun pillState(): PillState = when {
        voice.listening.value -> PillState.LISTENING
        voice.speaking.value -> PillState.SPEAKING
        else -> PillState.IDLE
    }

    private fun observeVoice() {
        scope.launch {
            voice.transcript.collect { transcript ->
                view?.update(
                    state = pillState(),
                    status = when (pillState()) {
                        PillState.LISTENING -> "Listening…"
                        PillState.SPEAKING -> "Speaking…"
                        PillState.IDLE -> "Tap to talk"
                    },
                    transcript = transcript,
                    amplitude = if (voice.listening.value) 0.5f else 0.1f,
                )
            }
        }
        scope.launch {
            voice.listening.collect { listening ->
                view?.update(
                    state = pillState(),
                    status = if (listening) "Listening…" else "Tap to talk",
                    transcript = voice.transcript.value,
                    amplitude = if (listening) 0.6f else 0f,
                )
            }
        }
        scope.launch {
            voice.speaking.collect { speaking ->
                view?.update(
                    state = pillState(),
                    status = if (speaking) "Speaking…" else "Tap to talk",
                    transcript = voice.transcript.value,
                    amplitude = if (speaking) 0.7f else 0f,
                )
            }
        }
        // Observe live RMS amplitude if available
        scope.launch {
            voice.rmsDb.collect { rms ->
                if (voice.listening.value) {
                    val normalized = ((rms + 2f) / 12f).coerceIn(0f, 1f)
                    view?.update(
                        state = pillState(),
                        status = "Listening…",
                        transcript = voice.transcript.value,
                        amplitude = normalized,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        view?.let { runCatching { windowManager?.removeView(it) } }
        view = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AuraOverlayService"

        const val ACTION_SHOW = "com.aura.aura_ui.overlay.SHOW"
        const val ACTION_SHOW_AND_LISTEN = "com.aura.aura_ui.overlay.SHOW_AND_LISTEN"
        const val ACTION_HIDE = "com.aura.aura_ui.overlay.HIDE"

        fun start(context: Context) {
            show(context)
        }

        fun show(context: Context, source: String = "app") {
            val intent = Intent(context, AuraOverlayService::class.java).setAction(ACTION_SHOW)
            context.startForegroundService(intent)
        }

        fun showAndListen(context: Context, source: String = "app") {
            val intent = Intent(context, AuraOverlayService::class.java).setAction(ACTION_SHOW_AND_LISTEN)
            context.startForegroundService(intent)
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, AuraOverlayService::class.java))
        }
    }
}
