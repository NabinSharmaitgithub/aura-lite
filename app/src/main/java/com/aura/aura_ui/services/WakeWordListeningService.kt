package com.aura.aura_ui.services

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aura.aura_ui.data.prefs.AuraSettings
import com.aura.aura_ui.notifications.AuraNotifications
import com.aura.aura_ui.overlay.AuraOverlayService
import com.aura.aura_ui.state.WakeWordState
import com.aura.aura_ui.voice.SherpaKwsWakeWordDetector
import com.aura.aura_ui.voice.WakeWordDetector
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Always-on offline wake word detection service.
 *
 * Runs as a foreground service with a quiet notification, listening for "Wake up AURA"
 * via sherpa-onnx KWS. When detected, triggers haptic feedback, shows the overlay,
 * and launches the voice assistant pipeline.
 */
@AndroidEntryPoint
class WakeWordListeningService : Service() {

    @Inject lateinit var settings: AuraSettings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var detector: WakeWordDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var resumeJob: Job? = null

    private fun setWakeState(state: WakeWordState) {
        _stateFlow.value = state
        currentState = state
    }

    override fun onCreate() {
        super.onCreate()
        AuraNotifications.ensureChannels(this)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!settings.wakeWordEnabled) {
            stopSelfSafely()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        startForeground(AuraNotifications.ID_WAKE, AuraNotifications.wakeWordNotification(this))
        startDetector()
        return START_STICKY
    }

    private fun startDetector() {
        resumeJob?.cancel()
        setWakeState(WakeWordState.STARTING)

        scope.launch(Dispatchers.IO) {
            try {
                if (detector == null) {
                    detector = SherpaKwsWakeWordDetector(
                        context = this@WakeWordListeningService,
                        keywordsScore = settings.wakeWordScore,
                        keywordsThreshold = settings.wakeWordThreshold,
                    ).apply {
                        setOnWakeWordDetected { keyword ->
                            onWakePhraseTriggered(keyword)
                        }
                    }
                }
                detector?.start()
                setWakeState(WakeWordState.DETECTING)
                Log.i(TAG, "✅ Wake word service active & listening for 'Wake up AURA'")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to start wake word detector: ${e.message}", e)
                setWakeState(WakeWordState.STOPPED)
                stopSelfSafely()
            }
        }
    }

    private fun onWakePhraseTriggered(keyword: String) {
        Log.i(TAG, "🎯 WAKE PHRASE TRIGGERED: $keyword")
        vibrateFeedback()

        setWakeState(WakeWordState.PAUSED_FOR_RUN)
        detector?.stop()

        scope.launch(Dispatchers.Main) {
            // Show assistant overlay and listen via Figma wake screen
            if (android.provider.Settings.canDrawOverlays(this@WakeWordListeningService)) {
                AuraOverlayService.showAndListen(this@WakeWordListeningService, source = "wake_word")
            } else {
                AssistantForegroundService.listen(this@WakeWordListeningService)
            }

            // Schedule detection resumption after voice turn finishes
            scheduleResumption()
        }
    }

    private fun scheduleResumption() {
        resumeJob?.cancel()
        resumeJob = scope.launch {
            // Wait while the voice interaction takes place (e.g. up to 20 seconds of silence/completion)
            delay(15_000)
            if (settings.wakeWordEnabled && isActive) {
                Log.i(TAG, "🔄 Resuming wake word detection after voice interaction")
                startDetector()
            } else {
                setWakeState(WakeWordState.STOPPED)
            }
        }
    }

    private fun vibrateFeedback() {
        runCatching {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(120)
            }
        }
    }

    private fun acquireWakeLock() {
        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "aura:wake_word_service_lock",
            ).apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L) // 1 hour safety cap
            }
        }
    }

    private fun stopSelfSafely() {
        setWakeState(WakeWordState.STOPPED)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        setWakeState(WakeWordState.STOPPED)
        resumeJob?.cancel()
        detector?.stop()
        detector?.release()
        detector = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "WakeWordService"

        private val _stateFlow = MutableStateFlow(WakeWordState.STOPPED)
        val stateFlow: StateFlow<WakeWordState> = _stateFlow.asStateFlow()

        @Volatile var currentState: WakeWordState = WakeWordState.STOPPED
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WakeWordListeningService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordListeningService::class.java))
        }
    }
}
