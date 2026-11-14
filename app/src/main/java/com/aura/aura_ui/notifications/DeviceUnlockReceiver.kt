package com.aura.aura_ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aura.aura_ui.agent.runtime.ActiveRunRegistry
import com.aura.aura_ui.data.prefs.AuraSettings
import com.aura.aura_ui.overlay.AuraOverlayService

/**
 * Triggered on ACTION_USER_PRESENT when the user unlocks their device.
 *
 * Inspects unread notifications: if an unread notification exists (e.g. WhatsApp, Messages, etc.),
 * wakes AURA to proactively announce:
 * "Hey boss, you've got a notification from <App>. May I read it for you?"
 * and handles the interactive read & reply voice conversation.
 */
class DeviceUnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_USER_PRESENT) return
        Log.i(TAG, "Device unlocked (ACTION_USER_PRESENT)")

        val settings = AuraSettings(context)
        if (!settings.wakeOnUnlockNotifications) {
            Log.d(TAG, "wakeOnUnlockNotifications is disabled in settings")
            return
        }

        // Do not interrupt an in-progress agent run
        if (ActiveRunRegistry.current() != null) {
            Log.d(TAG, "Agent run currently active; skipping unlock notification check")
            return
        }

        if (!AuraNotificationListenerService.isAccessGranted(context)) {
            Log.d(TAG, "Notification listener access not granted by user")
            return
        }

        val unread = AuraNotificationListenerService.getLatestUnreadNotification(context)
        if (unread == null) {
            Log.d(TAG, "No unread notifications to announce")
            return
        }

        // Prevent announcing the exact same notification twice
        if (unread.key == lastHandledKey && unread.postTime == lastHandledTime) {
            Log.d(TAG, "Notification ${unread.key} already handled previously")
            return
        }
        lastHandledKey = unread.key
        lastHandledTime = unread.postTime

        Log.i(TAG, "Waking AURA for notification from ${unread.appName} (${unread.sender})")
        AuraOverlayService.startNotificationFlow(
            context = context,
            key = unread.key,
            appName = unread.appName,
            sender = unread.sender,
            text = unread.text,
            replyAction = unread.replyActionTitle,
        )
    }

    companion object {
        private const val TAG = "DeviceUnlockReceiver"
        private var lastHandledKey: String? = null
        private var lastHandledTime: Long = 0L
    }
}
