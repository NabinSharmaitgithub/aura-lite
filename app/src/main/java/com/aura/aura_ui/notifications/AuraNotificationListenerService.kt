package com.aura.aura_ui.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.aura.mcp.bridge.ToolError
import com.aura.mcp.bridge.ToolResult
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class UnreadNotification(
    val key: String,
    val packageName: String,
    val appName: String,
    val sender: String,
    val text: String,
    val replyActionTitle: String?,
    val postTime: Long,
)

/**
 * AURA's notification listener — the agent's event sense and proactive assistant bridge.
 *
 * Provides:
 * 1. Live notification observation and status bar snapshots.
 * 2. RemoteInput direct reply (fireAction) so Aura can reply to WhatsApp, Telegram, Messages, etc.
 * 3. Querying latest unread notification for proactive wake-on-unlock assistant conversation.
 * 4. Backward-compatible read/act/dismiss ToolResult methods for AuraDeviceBridge.
 */
class AuraNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        current = this
        instance = this
        Log.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        if (current === this) current = null
        if (instance === this) instance = null
        Log.i(TAG, "Notification listener disconnected")
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (current === this) current = null
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Live status-bar snapshot; empty when listener is not bound or system returns null. */
    fun snapshots(): List<StatusBarNotification> =
        runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()

    /**
     * Fire the [Notification.Action] titled [actionTitle] on the notification with [key].
     * When [replyText] is provided, the action's [RemoteInput] is populated for inline direct reply.
     */
    fun fireAction(key: String, actionTitle: String, replyText: String?): String? {
        val sbn = snapshots().firstOrNull { it.key == key }
            ?: return "notification no longer active"
        val actions = sbn.notification.actions.orEmpty()
        val action = actions.firstOrNull {
            it.title?.toString()?.trim()?.equals(actionTitle.trim(), ignoreCase = true) == true ||
                (!it.remoteInputs.isNullOrEmpty() && actionTitle.contains("reply", ignoreCase = true))
        } ?: actions.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
          ?: actions.firstOrNull()
          ?: return "action '$actionTitle' not found"

        val pendingIntent = action.actionIntent ?: return "action has no intent"

        return runCatching {
            if (replyText != null) {
                val remoteInputs = action.remoteInputs
                    ?: return "action does not accept reply text"
                val fillIn = Intent()
                val results = Bundle()
                remoteInputs.forEach { results.putCharSequence(it.resultKey, replyText) }
                RemoteInput.addResultsToIntent(remoteInputs, fillIn, results)
                pendingIntent.send(this, 0, fillIn)
            } else {
                pendingIntent.send()
            }
            null
        }.getOrElse { t ->
            when (t) {
                is PendingIntent.CanceledException -> "action intent was cancelled by its app"
                else -> t.message ?: "failed to fire action"
            }
        }
    }

    /** Dismiss by key. Returns null on success, error text on failure. */
    fun dismiss(key: String): String? = runCatching {
        cancelNotification(key)
        null as String?
    }.getOrElse { it.message ?: "failed to dismiss" }

    companion object {
        private const val TAG = "AuraNotifListener"

        @Volatile
        var current: AuraNotificationListenerService? = null
            private set

        @Volatile
        var instance: AuraNotificationListenerService? = null
            private set

        /** Whether the user has granted Notification access to AURA. */
        fun isAccessGranted(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)

        fun isEnabled(context: Context): Boolean = isAccessGranted(context)

        fun componentName(context: Context): ComponentName =
            ComponentName(context, AuraNotificationListenerService::class.java)

        fun active(context: Context): List<StatusBarNotification> {
            val service = current ?: instance
            if (service != null) {
                return service.snapshots()
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            return manager?.activeNotifications?.toList().orEmpty()
        }

        fun getLatestUnreadNotification(context: Context, maxAgeMs: Long = 30 * 60 * 1000L): UnreadNotification? {
            val service = current ?: instance ?: return null
            val now = System.currentTimeMillis()
            val pm = context.packageManager

            val list = service.snapshots()
                .filterNot { sbn ->
                    val n = sbn.notification
                    sbn.packageName == context.packageName ||
                        sbn.isOngoing ||
                        (n.flags and Notification.FLAG_ONGOING_EVENT != 0) ||
                        (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) ||
                        (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) ||
                        sbn.packageName.startsWith("com.android.systemui") ||
                        sbn.packageName.startsWith("com.android.providers.downloads") ||
                        sbn.packageName == "android"
                }
                .filter { sbn ->
                    val n = sbn.notification
                    val extras = n.extras
                    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                        ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    !text.isNullOrBlank() && (now - sbn.postTime) <= maxAgeMs
                }
                .sortedByDescending { it.postTime }

            val newest = list.firstOrNull() ?: return null
            val n = newest.notification
            val extras = n.extras
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: return null

            val appName = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(newest.packageName, 0)).toString()
            }.getOrDefault(newest.packageName.substringAfterLast('.'))

            val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
                ?: appName

            val replyAction = n.actions.orEmpty().firstOrNull { action ->
                !action.remoteInputs.isNullOrEmpty() || action.title?.toString()?.contains("reply", ignoreCase = true) == true
            }?.title?.toString()

            return UnreadNotification(
                key = newest.key,
                packageName = newest.packageName,
                appName = appName,
                sender = sender,
                text = text.trim(),
                replyActionTitle = replyAction,
                postTime = newest.postTime,
            )
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
            val service = current ?: instance ?: return ToolResult.permission(
                "Notification access is not enabled",
                "settings/permissions",
            )
            val err = service.fireAction(key, action, null)
            return if (err == null) {
                ToolResult.ok(changed = true, observed = buildJsonObject { put("action", action) })
            } else {
                ToolResult.fail(ToolError.notFound(err))
            }
        }

        fun dismiss(context: Context, key: String): ToolResult {
            val service = current ?: instance ?: return ToolResult.permission(
                "Notification access is not enabled",
                "settings/permissions",
            )
            val err = service.dismiss(key)
            return if (err == null) {
                ToolResult.ok(changed = true)
            } else {
                ToolResult.fail(ToolError.notFound(err))
            }
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
