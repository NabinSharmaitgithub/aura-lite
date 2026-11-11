package com.aura.aura_ui.accessibility

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.Settings
import com.aura.aura_ui.notifications.AuraNotificationListenerService
import com.aura.mcp.bridge.ToolError
import com.aura.mcp.bridge.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Device status, volume, media transport and system intents. */
@Singleton
class SystemControlsHelper @Inject constructor(@ApplicationContext private val context: Context) {

    private val audio by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    fun deviceStatus(): JsonObject {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = battery.isCharging
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val alarm = (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = connectivity.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        val network = when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        val dnd = (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        return buildJsonObject {
            put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("android", Build.VERSION.RELEASE)
            put("api_level", Build.VERSION.SDK_INT)
            put("battery_percent", level)
            put("charging", charging)
            put("network", network)
            put("screen_on", power.isInteractive)
            put("accessibility_running", AuraAccessibilityService.isConnected())
            put("next_alarm", alarm?.triggerTime?.toString() ?: "")
            put("do_not_disturb", dnd)
            put("sdk_int", Build.VERSION.SDK_INT)
            put("volume_music", volume("music").getOrNull() ?: -1)
        }
    }

    fun volume(streamName: String): Result<Int> = runCatching {
        audio.getStreamVolume(stream(streamName))
    }

    fun changeVolume(streamName: String, action: String, level: Int?): ToolResult {
        val stream = stream(streamName)
        val max = audio.getStreamMaxVolume(stream)
        val current = audio.getStreamVolume(stream)
        when (action) {
            "get" -> return ToolResult.ok(
                observed = buildJsonObject { put("stream", streamName); put("level", current); put("max", max) },
            )
            "up" -> audio.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "down" -> audio.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            "unmute" -> audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
            "set" -> {
                val target = (level ?: current).coerceIn(0, max)
                if (Build.VERSION.SDK_INT >= 23) {
                    runCatching {
                        audio.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI)
                    }
                }
            }
            else -> return ToolResult.fail(ToolError("invalid_args", "Unknown volume action $action"))
        }
        return ToolResult.ok(
            changed = true,
            observed = buildJsonObject {
                put("stream", streamName)
                put("level", audio.getStreamVolume(stream))
                put("max", max)
            },
        )
    }

    fun mediaControl(action: String, packageName: String?): ToolResult {
        val controllers = mediaControllers().filter { packageName == null || it.packageName == packageName }
        val controller = controllers.firstOrNull()
            ?: return ToolResult.permission("No active media session", "notification_listener")
        val controls = controller.transportControls
        when (action) {
            "play" -> controls.play()
            "pause" -> controls.pause()
            "play_pause" -> controls.play()
            "next" -> controls.skipToNext()
            "previous" -> controls.skipToPrevious()
            "stop" -> controls.stop()
            else -> return ToolResult.fail(ToolError("invalid_args", "Unknown media action $action"))
        }
        return ToolResult.ok(
            changed = true,
            observed = buildJsonObject {
                put("package", controller.packageName)
                put("title", controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "")
                put("action", action)
            },
        )
    }

    fun mediaSessions(): ToolResult {
        val sessions = mediaControllers().map { controller ->
            buildJsonObject {
                put("package", controller.packageName)
                put("title", controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "")
                put("artist", controller.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "")
                put("playing", controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING)
            }
        }
        return ToolResult.ok(observed = buildJsonObject {
            put("sessions", buildJsonArray { sessions.forEach { add(it) } })
        })
    }

    private fun mediaControllers(): List<MediaController> = runCatching {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        manager.getActiveSessions(ComponentName(context, AuraNotificationListenerService::class.java))
    }.getOrDefault(emptyList())

    fun systemIntent(action: String, value: String?, packageName: String?): ToolResult {
        val intent = when (action) {
            "wifi_settings" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth_settings" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "battery_settings" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "app_settings" -> Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName ?: context.packageName, null),
            )
            "alarm" -> Intent(AlarmClock.ACTION_SET_ALARM).apply {
                val parts = value?.split(":")?.mapNotNull { it.trim().toIntOrNull() }
                if (parts != null && parts.size >= 2) {
                    putExtra(AlarmClock.EXTRA_HOUR, parts[0])
                    putExtra(AlarmClock.EXTRA_MINUTES, parts[1])
                }
            }
            "timer" -> Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, value?.toIntOrNull() ?: 300)
            }
            "share" -> Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, value.orEmpty())
            }
            "open_url" -> Intent(Intent.ACTION_VIEW, Uri.parse(value.orEmpty()))
            "brightness" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "airplane_mode" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            "dnd" -> Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
            else -> return ToolResult.fail(ToolError("invalid_args", "Unknown system action $action"))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            ToolResult.ok(changed = true, observed = buildJsonObject { put("opened", action) })
        }.getOrElse { ToolResult.fail(ToolError.internal(it.message ?: "Could not open $action")) }
    }

    private fun stream(name: String): Int = when (name) {
        "ring" -> AudioManager.STREAM_RING
        "alarm" -> AudioManager.STREAM_ALARM
        "notification" -> AudioManager.STREAM_NOTIFICATION
        "call" -> AudioManager.STREAM_VOICE_CALL
        else -> AudioManager.STREAM_MUSIC
    }
}
