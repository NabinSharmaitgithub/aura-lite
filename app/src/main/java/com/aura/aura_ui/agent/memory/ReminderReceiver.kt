package com.aura.aura_ui.agent.memory

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aura.aura_ui.R
import com.aura.aura_ui.data.db.ReminderDao
import com.aura.aura_ui.notifications.AuraNotifications
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var dao: ReminderDao

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val text = intent.getStringExtra(EXTRA_TEXT) ?: "Reminder"
        AuraNotifications.ensureChannels(context)
        val notification = NotificationCompat.Builder(context, AuraNotifications.CHANNEL_RUN)
            .setSmallIcon(R.drawable.ic_aura_mark)
            .setContentTitle("AURA reminder")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(AuraNotifications.openApp(context))
            .build()
        (context.getSystemService(NotificationManager::class.java))
            ?.notify(3000 + id.toInt().coerceAtLeast(0), notification)
        if (id >= 0) {
            CoroutineScope(Dispatchers.IO).launch { dao.markFired(id) }
        }
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_TEXT = "reminder_text"
    }
}
