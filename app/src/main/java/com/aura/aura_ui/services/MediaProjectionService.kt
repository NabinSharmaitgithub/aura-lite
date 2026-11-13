package com.aura.aura_ui.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.aura.aura_ui.notifications.AuraNotifications
import com.aura.aura_ui.state.ProjectionServiceState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds a `mediaProjection` foreground-service type while the screen-capture consent flow is in
 * progress (and captures execute). Without this, VirtualDisplay creation on Android 14+ throws
 * SecurityException and the app dies right after the user taps "Allow".
 */
@AndroidEntryPoint
class MediaProjectionService : Service() {

    private val _state = MutableStateFlow(ProjectionServiceState.CREATED)
    /** Explicit media-projection service lifecycle. */
    val state: StateFlow<ProjectionServiceState> = _state.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        AuraNotifications.ensureChannels(this)
        _state.value = ProjectionServiceState.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 14+ requires the foreground service type to be supplied explicitly when the
        // service runs a mediaProjection capture; the type-less startForeground overload is not
        // sufficient and can crash right after the user grants capture access.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            startForeground(
                AuraNotifications.ID_PROJECTION,
                AuraNotifications.projectionNotification(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(AuraNotifications.ID_PROJECTION, AuraNotifications.projectionNotification(this))
        }
        _state.value = ProjectionServiceState.FOREGROUND
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _state.value = ProjectionServiceState.STOPPED
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, MediaProjectionService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, MediaProjectionService::class.java)) }
        }
    }
}
