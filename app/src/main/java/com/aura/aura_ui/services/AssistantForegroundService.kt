package com.aura.aura_ui.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.aura.aura_ui.agent.AgentRuntime
import com.aura.aura_ui.agent.report.RunEntryMode
import com.aura.aura_ui.notifications.AuraNotifications
import com.aura.aura_ui.state.ForegroundServiceState
import com.aura.aura_ui.voice.VoiceEngine
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.app.NotificationManager

/**
 * Hosts a run in the foreground so Android does not freeze it while the user is in another app.
 * Voice runs start here too, which keeps the microphone and the run under one service.
 */
@AndroidEntryPoint
class AssistantForegroundService : Service() {

    @Inject lateinit var runtime: AgentRuntime
    @Inject lateinit var voice: VoiceEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var listenJob: Job? = null

    private val _state = MutableStateFlow(ForegroundServiceState.CREATED)
    /** Explicit foreground-service lifecycle. */
    val state: StateFlow<ForegroundServiceState> = _state.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        AuraNotifications.ensureChannels(this)
        _state.value = ForegroundServiceState.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        _state.value = ForegroundServiceState.RUNNING
        startForeground(AuraNotifications.ID_RUN, AuraNotifications.runNotification(this, runtime.state.value))
        when (intent?.action) {
            ACTION_LISTEN -> { _state.value = ForegroundServiceState.LISTENING; beginListening() }
            ACTION_PAUSE -> runtime.pause()
            ACTION_RESUME -> runtime.resume()
            ACTION_STOP -> runtime.stop()
            else -> intent?.getStringExtra(EXTRA_GOAL)?.takeIf { it.isNotBlank() }?.let { goal ->
                val entryMode = intent?.getStringExtra(EXTRA_ENTRY_MODE)
                    ?.let { runCatching { RunEntryMode.valueOf(it) }.getOrNull() }
                    ?: RunEntryMode.FRESH
                runtime.submit(goal, entryMode = entryMode)
            }
        }
        observeRuntime()
        return START_NOT_STICKY
    }

    private fun beginListening() {
        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            notify(
                AuraNotifications.DPI_NO_MIC,
                "Microphone permission is off",
                "Open Settings → Permissions and grant Microphone to use voice.",
            )
            stopIfIdle()
            return
        }
        if (listenJob?.isActive == true) return
        listenJob = scope.launch {
            val result = voice.listen()
            result.onSuccess { transcript ->
                if (transcript.isNotBlank()) runtime.submit(transcript, voice = true)
                else stopIfIdle()
            }.onFailure { error ->
                notify(
                    AuraNotifications.DPI_FAILURE,
                    "Voice capture failed",
                    error.message ?: "The microphone could not be used right now.",
                )
                stopIfIdle()
            }
        }
    }

    private fun notify(id: Int, title: String, body: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(id, AuraNotifications.agentFailureNotification(this, title, body))
    }

    private fun observeRuntime() {
        if (collectJob?.isActive == true) return
        collectJob = scope.launch {
            runtime.state.collectLatest { state ->
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(AuraNotifications.ID_RUN, AuraNotifications.runNotification(this@AssistantForegroundService, state))
                if (!state.running && state.outcome != null) {
                    if (!state.answer.isNullOrBlank() && voice.speaking.value.not()) {
                        voice.speak(state.answer)
                    }
                    delay(1200)
                    stopIfIdle()
                }
            }
        }
    }

    private fun stopIfIdle() {
        if (!runtime.isRunning) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        _state.value = ForegroundServiceState.STOPPED
        collectJob?.cancel()
        listenJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_LISTEN = "com.aura.ACTION_LISTEN"
        const val ACTION_PAUSE = "com.aura.ACTION_PAUSE"
        const val ACTION_RESUME = "com.aura.ACTION_RESUME"
        const val ACTION_STOP = "com.aura.ACTION_STOP"
        const val EXTRA_GOAL = "goal"
        const val EXTRA_ENTRY_MODE = "entry_mode"

        fun submit(context: Context, goal: String, entryMode: RunEntryMode = RunEntryMode.FRESH) {
            context.startForegroundService(
                Intent(context, AssistantForegroundService::class.java)
                    .putExtra(EXTRA_GOAL, goal)
                    .putExtra(EXTRA_ENTRY_MODE, entryMode.name),
            )
        }

        fun listen(context: Context) {
            context.startForegroundService(
                Intent(context, AssistantForegroundService::class.java).setAction(ACTION_LISTEN),
            )
        }

        fun control(context: Context, action: String) {
            context.startForegroundService(
                Intent(context, AssistantForegroundService::class.java).setAction(action),
            )
        }
    }
}
