package com.aura.aura_ui.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.aura.aura_ui.agent.AgentRuntime
import com.aura.aura_ui.notifications.AuraNotifications
import com.aura.aura_ui.voice.VoiceEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

    override fun onCreate() {
        super.onCreate()
        AuraNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(AuraNotifications.ID_RUN, AuraNotifications.runNotification(this, runtime.state.value))
        when (intent?.action) {
            ACTION_LISTEN -> beginListening()
            ACTION_PAUSE -> runtime.pause()
            ACTION_RESUME -> runtime.resume()
            ACTION_STOP -> runtime.stop()
            else -> intent?.getStringExtra(EXTRA_GOAL)?.takeIf { it.isNotBlank() }?.let { goal ->
                runtime.submit(goal)
            }
        }
        observeRuntime()
        return START_NOT_STICKY
    }

    private fun beginListening() {
        if (listenJob?.isActive == true) return
        listenJob = scope.launch {
            val result = voice.listen()
            result.onSuccess { transcript ->
                if (transcript.isNotBlank()) runtime.submit(transcript, voice = true)
                else stopIfIdle()
            }.onFailure {
                stopIfIdle()
            }
        }
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

        fun submit(context: Context, goal: String) {
            context.startForegroundService(
                Intent(context, AssistantForegroundService::class.java).putExtra(EXTRA_GOAL, goal),
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
