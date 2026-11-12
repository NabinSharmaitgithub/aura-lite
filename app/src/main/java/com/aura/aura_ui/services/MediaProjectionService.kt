package com.aura.aura_ui.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.aura.aura_ui.notifications.AuraNotifications
import dagger.hilt.android.AndroidEntryPoint

/**
 * Holds a `mediaProjection` foreground-service type while the screen-capture consent flow is in
 * progress (and captures execute). Without this, VirtualDisplay creation on Android 14+ throws
 * SecurityException and the app dies right after the user taps "Allow".
 */
@AndroidEntryPoint
class MediaProjectionService : Service() {

    override fun onCreate() {
        super.onCreate()
        AuraNotifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(AuraNotifications.ID_PROJECTION, AuraNotifications.projectionNotification(this))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
