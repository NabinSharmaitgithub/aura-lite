package com.aura.aura_ui.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aura.aura_ui.agent.memory.ReminderScheduler
import com.aura.aura_ui.data.db.ReminderDao
import com.aura.aura_ui.data.prefs.AuraSettings
import com.aura.aura_ui.services.WakeWordListeningService
import com.aura.aura_ui.worker.RetentionWorker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores only state the user opted into. Never starts the agent, never starts the wake service
 * unless wake_word_enabled is true.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: AuraSettings
    @Inject lateinit var reminders: ReminderScheduler
    @Inject lateinit var reminderDao: ReminderDao

    override fun onReceive(context: Context, intent: Intent) {
        if (settings.wakeWordEnabled) {
            runCatching { WakeWordListeningService.start(context) }
        }
        RetentionWorker.enqueue(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                reminderDao.due(Long.MAX_VALUE).forEach { reminder ->
                    if (!reminder.fired) {
                        reminders.scheduleAlarm(reminder.id, reminder.text, reminder.triggerAt)
                    }
                }
            }
            pending.finish()
        }
    }
}
