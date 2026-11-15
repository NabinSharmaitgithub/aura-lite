package com.aura.aura_ui.agent.memory

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Intent
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule(text: String, atEpochMs: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
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
        val intent = Intent(context, ReminderReceiver::class.java)
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
        val intent = Intent(context, ReminderReceiver::class.java).apply {
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
        private const val TAG = "ReminderScheduler"
        const val EXTRA_ID = "commitment_id"
        const val OVERDUE_GRACE_MS = 2_000L

        fun triggerAt(dueAtEpochMs: Long, nowEpochMs: Long = System.currentTimeMillis()): Long =
            if (dueAtEpochMs > nowEpochMs) dueAtEpochMs else nowEpochMs + OVERDUE_GRACE_MS

        fun schedule(context: Context, c: Commitment) {
            if (c.done) return
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val at = triggerAt(c.dueAtEpochMs, System.currentTimeMillis())
            val pi = PendingIntent.getBroadcast(
                context,
                c.id.hashCode(),
                Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, c.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            runCatching {
                if (exact) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            }.onFailure { Log.w(TAG, "could not arm reminder ${c.id}: ${it.message}") }
        }

        fun rescheduleAll(context: Context) {
            runCatching { memoryService(context).pendingCommitments().forEach { schedule(context, it) } }
                .onFailure { Log.w(TAG, "re-arm after boot failed: ${it.message}") }
        }
    }
}