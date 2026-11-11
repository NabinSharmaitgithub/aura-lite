package com.aura.aura_ui.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aura.aura_ui.agent.AgentRuntime
import com.aura.aura_ui.agent.ConfirmationBroker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Pause/stop from the run notification and approve/deny from the MCP approval notification. */
@AndroidEntryPoint
class RunActionReceiver : BroadcastReceiver() {

    @Inject lateinit var runtime: AgentRuntime
    @Inject lateinit var confirmations: ConfirmationBroker

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.aura.ACTION_TOGGLE_PAUSE" -> {
                if (runtime.state.value.paused) runtime.resume() else runtime.pause()
            }
            "com.aura.ACTION_STOP_RUN" -> runtime.stop()
            "com.aura.ACTION_MCP_APPROVE" -> {
                intent.getStringExtra("request_id")?.let { confirmations.respond(it, true) }
            }
            "com.aura.ACTION_MCP_DENY" -> {
                intent.getStringExtra("request_id")?.let { confirmations.respond(it, false) }
            }
        }
    }
}
