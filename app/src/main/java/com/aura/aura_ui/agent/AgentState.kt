package com.aura.aura_ui.agent

import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class TraceLine(
    val seq: Long,
    val type: String,
    val title: String,
    val detail: String? = null,
    val ok: Boolean? = null,
    val name: String? = null,
    val args: String? = null,
    val durationMs: Long? = null,
    val at: Long = System.currentTimeMillis(),
)

data class AgentRunState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val sessionId: String? = null,
    val goal: String = "",
    val status: String = "idle",
    val trace: List<TraceLine> = emptyList(),
    val streamText: String = "",
    val answer: String? = null,
    val error: String? = null,
    val outcome: String? = null,
    val stepCount: Int = 0,
    val provider: String = "",
    val model: String = "",
    val startedAt: Long = 0L,
) {
    val displayStatus: String
        get() = when {
            !running && outcome == null -> "idle"
            paused -> "paused"
            else -> status
        }
}

data class ConfirmationRequest(
    val id: String,
    val title: String,
    val detail: String,
    val tool: String? = null,
)

/**
 * HITL gate. Destructive goals and risky MCP calls suspend here until the user answers.
 * A missing answer keeps the run paused; it never silently proceeds.
 */
@Singleton
class ConfirmationBroker @Inject constructor() {
    private val _pending = MutableStateFlow<ConfirmationRequest?>(null)
    val pending: StateFlow<ConfirmationRequest?> = _pending.asStateFlow()

    private val waiters = mutableMapOf<String, CompletableDeferred<Boolean>>()

    fun request(title: String, detail: String, tool: String? = null): ConfirmationRequest {
        val request = ConfirmationRequest(UUID.randomUUID().toString().take(8), title, detail, tool)
        waiters[request.id] = CompletableDeferred()
        _pending.value = request
        return request
    }

    fun await(request: ConfirmationRequest): CompletableDeferred<Boolean> =
        waiters.getOrPut(request.id) { CompletableDeferred() }

    fun respond(id: String, approved: Boolean) {
        waiters.remove(id)?.complete(approved)
        if (_pending.value?.id == id) _pending.value = null
    }

    fun cancelAll() {
        waiters.values.forEach { it.complete(false) }
        waiters.clear()
        _pending.value = null
    }
}
