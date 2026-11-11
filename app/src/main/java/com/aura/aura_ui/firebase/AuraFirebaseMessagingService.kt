package com.aura.aura_ui.firebase

import com.aura.aura_ui.R
import com.aura.aura_ui.data.prefs.AuraSettings
import com.aura.aura_ui.notifications.AuraNotifications
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Optional push channel. AURA sends data-safe notifications (title/body) that open the app.
 * The token is stored in [AuraSettings] so users can see it in Settings → Firebase; nothing is
 * sent without a user linking an account and a backend that knows this token.
 */
class AuraFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        runCatching { AuraSettings(applicationContext).firebaseFcmToken = token }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification
        val title = notification?.title ?: message.data["title"]?.takeIf { it.isNotBlank() } ?: "AURA"
        val body = notification?.body ?: message.data["body"]?.takeIf { it.isNotBlank() } ?: return
        AuraNotifications.ensureChannels(applicationContext)
        val built = AuraNotifications.fcmNotification(applicationContext, title, body)
        androidx.core.app.NotificationManagerCompat.from(this).notify(AuraNotifications.ID_FCM, built)
    }
}
