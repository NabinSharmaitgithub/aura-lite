package com.aura.aura_ui.agent.memory

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aura.aura_ui.MainActivity
import com.aura.aura_ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Makes a stored [Commitment] ring at its due time. Before this a reminder only surfaced if the
 * user happened to start a conversation after it was due.
 *
 * Exact when the user granted "Alarms & reminders"; otherwise an inexact allow-while-idle alarm,
 * which Doze may hold for some minutes — late, but it still rings.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderScheduler.EXTRA_ID) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val memory = memoryService(app)
                // Deleted or already completed from the Memory screen since it was armed: stay quiet.
                val c = memory.pendingCommitments().firstOrNull { it.id == id } ?: return@launch
                if (notify(app, c)) memory.markDelivered(listOf(c.id))
            } catch (t: Throwable) {
                Log.w(TAG, "reminder $id failed: ${t.message}")
            } finally {
                pending.finish()
            }
        }
    }

    /** False when notifications are blocked — the reminder stays pending for the next conversation. */
    @SuppressLint("MissingPermission") // checked: areNotificationsEnabled() covers POST_NOTIFICATIONS
    private fun notify(context: Context, c: Commitment): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val open = PendingIntent.getActivity(
            context,
            c.id.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reminder")
            .setContentText(c.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(c.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        return runCatching { nm.notify(c.id.hashCode(), notification) }.isSuccess
    }

    private companion object {
        const val TAG = "ReminderReceiver"
        const val CHANNEL_ID = "aura_reminders"
    }
}
