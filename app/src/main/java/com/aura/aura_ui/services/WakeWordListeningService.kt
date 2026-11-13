package com.aura.aura_ui.services

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aura.aura_ui.data.prefs.AuraSettings
import com.aura.aura_ui.notifications.AuraNotifications
import com.aura.aura_ui.state.WakeWordState
import com.aura.aura_ui.voice.VadGate
import com.aura.aura_ui.voice.WakeWordEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Offline wake word. Starts only when the user explicitly enables it (never from Application or
 * the accessibility service). Stops capture while a voice run uses the microphone, then resumes.
 */
@AndroidEntryPoint
class WakeWordListeningService : Service() {

    @Inject lateinit var settings: AuraSettings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var engine: WakeWordEngine? = null
    @Volatile private var wakeState: WakeWordState = WakeWordState.STOPPED

    private fun setWakeState(state: WakeWordState) {
        wakeState = state
        currentState = state
    }

    override fun onCreate() {
        super.onCreate()
        AuraNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!settings.wakeWordEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(AuraNotifications.ID_WAKE, AuraNotifications.wakeWordNotification(this))
        startDetection()
        return START_STICKY
    }

    private fun startDetection() {
        if (job?.isActive == true) return
        setWakeState(WakeWordState.STARTING)
        job = scope.launch {
            val loaded = WakeWordEngine.create(this@WakeWordListeningService)
            if (loaded == null) {
                // Model not bundled: never leave a microphone service running silently.
                setWakeState(WakeWordState.STOPPING)
                delay(800)
                stopSelfSafely()
                return@launch
            }
            engine = loaded
            val sampleRate = 16000
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, sampleRate / 5),
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                stopSelfSafely()
                return@launch
            }
            val vad = VadGate(settings.vadSensitivity)
            val buffer = ShortArray(1600)
            val floats = FloatArray(1600)
            try {
                record.startRecording()
                setWakeState(WakeWordState.DETECTING)
                while (isActive && wakeState == WakeWordState.DETECTING) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    // Feed every frame: keyword spotting needs continuous context, VAD only trims work.
                    for (i in 0 until read) floats[i] = buffer[i] / 32768f
                    val samples = if (read == floats.size) floats else floats.copyOf(read)
                    val hit = engine?.accept(samples, sampleRate) == true
                    if (hit) {
                        setWakeState(WakeWordState.PAUSED_FOR_RUN)
                        AssistantForegroundService.listen(this@WakeWordListeningService)
                        break
                    }
                }
            } finally {
                runCatching { record.stop() }
                runCatching { record.release() }
            }
            // Give the voice run time to take the microphone, then resume detection.
            if (wakeState == WakeWordState.PAUSED_FOR_RUN) {
                delay(25_000)
                setWakeState(WakeWordState.DETECTING)
                if (settings.wakeWordEnabled) startDetection()
            } else {
                setWakeState(WakeWordState.STOPPED)
            }
        }
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        setWakeState(WakeWordState.STOPPED)
        job?.cancel()
        engine?.release()
        engine = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
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
