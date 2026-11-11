package com.aura.aura_ui.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import com.aura.aura_ui.notifications.AuraNotifications
import com.aura.aura_ui.services.AssistantForegroundService
import com.aura.aura_ui.voice.VoiceEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Floating assistant surface. Opt-in via Companion/Voice settings; shows live transcript while
 * listening and lets the user cancel without opening the app.
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
            stopSelf()
            return START_NOT_STICKY
        }
        if (view == null) showPill()
        observeVoice()
        return START_STICKY
    }

    private fun showPill() {
        val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = manager
        val pill = OverlayPillView(this)
        pill.onTap = {
            if (voice.listening.value) {
                voice.cancelListening()
                pill.update(false, voice.speaking.value, "Tap to talk", "")
            } else {
                AssistantForegroundService.listen(this)
            }
        }
        pill.onLongPress = { stopSelf() }
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
            y = 120
        }
        runCatching { manager.addView(pill, params) }
        view = pill
    }

    private fun observeVoice() {
        scope.launch {
            voice.transcript.collect { transcript ->
                view?.update(
                    listening = voice.listening.value,
                    speaking = voice.speaking.value,
                    status = when {
                        voice.listening.value -> "Listening…"
                        voice.speaking.value -> "Speaking…"
                        else -> "Tap to talk"
                    },
                    transcript = transcript,
                )
            }
        }
        scope.launch {
            voice.listening.collect { listening ->
                view?.update(
                    listening = listening,
                    speaking = voice.speaking.value,
                    status = if (listening) "Listening…" else "Tap to talk",
                    transcript = voice.transcript.value,
                )
            }
        }
        scope.launch {
            voice.speaking.collect { speaking ->
                view?.update(
                    listening = voice.listening.value,
                    speaking = speaking,
                    status = if (speaking) "Speaking…" else "Tap to talk",
                    transcript = voice.transcript.value,
                )
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
}
