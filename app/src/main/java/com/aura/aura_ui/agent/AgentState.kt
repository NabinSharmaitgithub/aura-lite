package com.aura.aura_ui.agent

import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import com.aura.aura_ui.agent.ledger.PlanStep
import com.aura.aura_ui.agent.report.RunEntryMode
import com.aura.aura_ui.agent.report.RunReport
import com.aura.aura_ui.agent.report.RunTermination
import com.aura.aura_ui.state.ApprovalState
import com.aura.aura_ui.state.RunPhase

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
    /** The single source of truth for what the run is doing. */
    val phase: RunPhase = RunPhase.IDLE,
    val sessionId: String? = null,
    val goal: String = "",
    val trace: List<TraceLine> = emptyList(),
    val streamText: String = "",
    val answer: String? = null,
    val error: String? = null,
    val outcome: String? = null,
    val stepCount: Int = 0,
    /** Plan steps maintained by the agent during the run. */
    val planSteps: List<PlanStep> = emptyList(),
    val cursor: AgentCursorState? = null,
    val provider: String = "",
    val model: String = "",
    val entryMode: RunEntryMode = RunEntryMode.FRESH,
    val termination: RunTermination? = null,
    /** Structured report built at termination; UI/forensics read this instead of model prose. */
    val report: RunReport? = null,
    val startedAt: Long = 0L,
) {
    /** Derived for backwards compatibility; [phase] is authoritative. */
    val running: Boolean get() = phase.isActive
    val paused: Boolean get() = phase == RunPhase.PAUSED
    val status: String get() = phase.wire

    val displayStatus: String
        get() = when {
            phase == RunPhase.IDLE && outcome == null -> "idle"
            phase == RunPhase.PAUSED -> "paused"
            else -> phase.wire
        }
}

/**
 * Live on-screen cursor position (px) for the agent. The UI renders an animated cursor that
 * follows the point of the next mutating action the agent is about to take.
 */
data class AgentCursorState(
    val x: Float = 0f,
    val y: Float = 0f,
)

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

    private val _state = MutableStateFlow<ApprovalState>(ApprovalState.Idle)
    /** Explicit approval state for diagnostics and tests: one visible request plus a FIFO queue. */
    val state: StateFlow<ApprovalState> = _state.asStateFlow()

    private val waiters = mutableMapOf<String, CompletableDeferred<Boolean>>()
    private val queue = ArrayDeque<ConfirmationRequest>()
    private val lock = Any()

    fun request(title: String, detail: String, tool: String? = null): ConfirmationRequest {
        val request = ConfirmationRequest(UUID.randomUUID().toString().take(8), title, detail, tool)
        synchronized(lock) {
            waiters[request.id] = CompletableDeferred()
            if (_pending.value == null) {
                _pending.value = request
            } else {
                // Never overwrite a visible approval; a later action waits its turn.
                queue.addLast(request)
            }
            publish()
        }
        return request
    }

    fun await(request: ConfirmationRequest): CompletableDeferred<Boolean> =
        synchronized(lock) { waiters.getOrPut(request.id) { CompletableDeferred() } }

    fun respond(id: String, approved: Boolean) {
        synchronized(lock) {
            waiters.remove(id)?.complete(approved)
            val current = _pending.value
            if (current?.id == id) {
                _pending.value = queue.removeFirstOrNull()
                publish()
            } else {
                // A queued request may be declined before it becomes visible.
                if (queue.removeAll { it.id == id }) publish()
            }
        }
    }

    fun cancelAll() {
        synchronized(lock) {
            waiters.values.forEach { it.complete(false) }
            waiters.clear()
            queue.clear()
            _pending.value = null
            publish()
        }
    }

    private fun publish() {
        val current = _pending.value
        _state.value = if (current == null) {
            ApprovalState.Idle
        } else {
            ApprovalState.Pending(
                request = ApprovalState.ConfirmationRequestView(
                    id = current.id,
                    title = current.title,
                    detail = current.detail,
                    tool = current.tool,
                ),
                queued = queue.size,
            )
        }
    }
}
