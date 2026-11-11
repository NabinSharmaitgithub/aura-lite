package com.aura.aura_ui

import android.app.Application
import com.aura.aura_ui.data.skills.SkillsRepository
import com.aura.aura_ui.notifications.AuraNotifications
import com.aura.aura_ui.worker.RetentionWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process entry point.
 *
 * Deliberately does not start the microphone, an overlay, an MCP socket or a model session.
 * Wake word, companion and MCP are user actions; retention and notifications are cheap and
 * safe to initialise here.
 */
@HiltAndroidApp
class AuraApplication : Application() {

    @Inject lateinit var skills: SkillsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AuraNotifications.ensureChannels(this)
        RetentionWorker.enqueue(this)
        scope.launch { runCatching { skills.ensureSeeded() } }
    }
}
