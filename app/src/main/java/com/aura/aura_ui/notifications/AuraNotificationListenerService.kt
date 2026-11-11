package com.aura.aura_ui.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.aura.mcp.bridge.ToolError
import com.aura.mcp.bridge.ToolResult
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Notification access. Used by read_notifications, notification_action and media transport
 * (MediaSessionManager requires an enabled listener).
 */
class AuraNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    companion object {
        @Volatile
        var instance: AuraNotificationListenerService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val component = ComponentName(context, AuraNotificationListenerService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == component }
        }

        fun active(context: Context): List<StatusBarNotification> {
            val service = instance
            if (service != null) {
                return runCatching { service.activeNotifications.toList() }.getOrDefault(emptyList())
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager?.activeNotifications?.toList().orEmpty()
        }

        fun read(context: Context, limit: Int, packageName: String?): ToolResult {
            val items = active(context)
                .filter { packageName == null || it.packageName == packageName }
                .sortedByDescending { it.postTime }
                .take(limit)
                .map { sbn -> sbn.toJson() }
            return ToolResult.ok(
                observed = buildJsonObject {
                    put("notifications", buildJsonArray { items.forEach { add(it) } })
                    put("count", items.size)
                },
            )
        }

        fun act(context: Context, key: String, action: String): ToolResult {
            val service = instance ?: return ToolResult.permission(
                "Notification access is not enabled",
                "settings/permissions",
            )
            val sbn = service.activeNotifications.firstOrNull { it.key == key }
                ?: return ToolResult.fail(ToolError.notFound("No notification with that key"))
            val actions = sbn.notification.actions.orEmpty()
            val target = actions.firstOrNull { it.title?.toString()?.equals(action, ignoreCase = true) == true }
                ?: actions.getOrNull(action.toIntOrNull() ?: -1)
            if (target == null) {
                service.dismissNotification(sbn)
                return ToolResult.ok(changed = true)
            }
            target.actionIntent.send()
            return ToolResult.ok(changed = true, observed = buildJsonObject { put("action", action) })
        }

        fun dismiss(context: Context, key: String): ToolResult {
            val service = instance ?: return ToolResult.permission(
                "Notification access is not enabled",
                "settings/permissions",
            )
            val sbn = service.activeNotifications.firstOrNull { it.key == key }
                ?: return ToolResult.fail(ToolError.notFound("No notification with that key"))
            service.dismissNotification(sbn)
            return ToolResult.ok(changed = true)
        }
    }
}

private fun StatusBarNotification.toJson() = buildJsonObject {
    val extras = notification.extras
    put("key", key)
    put("package", packageName)
    put("title", extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty())
    put("text", extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty())
    put("posted_at", postTime)
    put("ongoing", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    put(
        "actions",
        buildJsonArray {
            notification.actions.orEmpty().forEach { add(it.title?.toString().orEmpty()) }
        },
    )
}
