package com.aura.aura_ui.agent.memory

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    private val context: Context,
) {
    fun schedule(text: String, atEpochMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, com.aura.aura_ui.receivers.ReminderReceiver::class.java).apply {
            putExtra("text", text)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            atEpochMs.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, pi)
    }

    fun cancel(id: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, com.aura.aura_ui.receivers.ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            am.cancel(pi)
            pi.cancel()
        }
    }

    fun scheduleAlarm(id: Long, text: String, triggerAt: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, com.aura.aura_ui.receivers.ReminderReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("text", text)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    companion object {
        const val OVERDUE_GRACE_MS = 500L

        fun triggerAt(dueAtEpochMs: Long, nowEpochMs: Long = System.currentTimeMillis()): Long =
            if (dueAtEpochMs >= nowEpochMs) dueAtEpochMs else nowEpochMs + OVERDUE_GRACE_MS

        fun schedule(context: Context, commitment: Commitment) {
            val trigger = triggerAt(commitment.dueAtEpochMs)
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, com.aura.aura_ui.receivers.ReminderReceiver::class.java).apply {
                putExtra("id", commitment.id)
                putExtra("text", commitment.text)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                commitment.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }
}
