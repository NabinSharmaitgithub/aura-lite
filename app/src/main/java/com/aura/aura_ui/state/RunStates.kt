package com.aura.aura_ui.state

/**
 * Canonical phases for a single agent run.
 *
 * This replaces the old combination of `running`, `paused` and a free-form `status` string.
 * [AgentRunState][com.aura.aura_ui.agent.AgentRunState] is now phase-first; the legacy booleans
 * and status string are derived from it.
 */
enum class RunPhase(val wire: String) {
    IDLE("idle"),
    PLANNING("planning"),
    ACTING("acting"),
    VERIFYING("verifying"),
    PAUSED("paused"),
    DONE("done"),
    PARTIAL("partial"),
    CANCELLED("cancelled"),
    ERROR("error"),
    ;

    val isActive: Boolean
        get() = this == PLANNING || this == ACTING || this == VERIFYING || this == PAUSED

    val isTerminal: Boolean
        get() = this == DONE || this == PARTIAL || this == CANCELLED || this == ERROR

    companion object {
        fun fromWire(value: String?): RunPhase =
            entries.firstOrNull { it.wire == value } ?: IDLE
    }
}

/** Events that can move a run between [RunPhase]s. */
sealed interface RunEvent {
    data object Start : RunEvent
    data object Plan : RunEvent
    data class Act(val tool: String? = null) : RunEvent
    data object Verify : RunEvent
    data object Pause : RunEvent
    data object Resume : RunEvent
    data class Terminate(val phase: RunPhase) : RunEvent
    data object Reset : RunEvent
}

/**
 * The only legal transition table for a run. Call [next] instead of assigning a phase directly.
 *
 * Illegal transitions throw [IllegalStateException]; the runtime catches this in debug builds and
 * logs it, but the important part is that a bad transition can no longer be silent.
 */
object RunTransitions {

    fun next(current: RunPhase, event: RunEvent): RunPhase {
        val next = when (event) {
            RunEvent.Start -> when (current) {
                RunPhase.IDLE, RunPhase.DONE, RunPhase.PARTIAL, RunPhase.CANCELLED, RunPhase.ERROR ->
                    RunPhase.PLANNING
                else -> illegal(current, event)
            }
            RunEvent.Plan -> when (current) {
                RunPhase.PLANNING, RunPhase.ACTING, RunPhase.VERIFYING, RunPhase.PAUSED ->
                    RunPhase.PLANNING
                else -> illegal(current, event)
            }
            is RunEvent.Act -> when (current) {
                RunPhase.PLANNING, RunPhase.ACTING, RunPhase.VERIFYING -> RunPhase.ACTING
                else -> illegal(current, event)
            }
            RunEvent.Verify -> when (current) {
                RunPhase.PLANNING, RunPhase.ACTING -> RunPhase.VERIFYING
                else -> illegal(current, event)
            }
            RunEvent.Pause -> when (current) {
                RunPhase.PLANNING, RunPhase.ACTING, RunPhase.VERIFYING -> RunPhase.PAUSED
                else -> illegal(current, event)
            }
            RunEvent.Resume -> when (current) {
                RunPhase.PAUSED -> RunPhase.PLANNING
                else -> illegal(current, event)
            }
            is RunEvent.Terminate -> when {
                event.phase.isTerminal -> event.phase
                else -> illegal(current, event)
            }
            RunEvent.Reset -> RunPhase.IDLE
        }
        return next
    }

    fun canTransition(from: RunPhase, to: RunPhase): Boolean =
        runCatching {
            next(
                from,
                when (to) {
                    RunPhase.PLANNING -> RunEvent.Plan
                    RunPhase.ACTING -> RunEvent.Act()
                    RunPhase.VERIFYING -> RunEvent.Verify
                    RunPhase.PAUSED -> RunEvent.Pause
                    RunPhase.IDLE -> RunEvent.Reset
                    else -> RunEvent.Terminate(to)
                },
            )
        }.isSuccess

    private fun illegal(current: RunPhase, event: RunEvent): Nothing =
        throw IllegalStateException("Illegal run transition: $current --${event::class.simpleName}-->")
}

/**
 * A typed command for the HITL approval broker.
 * A request is queued rather than overwriting the visible one.
 */
sealed interface ApprovalState {
    data object Idle : ApprovalState
    data class Pending(val request: ConfirmationRequestView, val queued: Int = 0) : ApprovalState

    /** Wire-friendly view, kept independent of the Android-free state package. */
    data class ConfirmationRequestView(
        val id: String,
        val title: String,
        val detail: String,
        val tool: String? = null,
    )
}

/** Ask-user HITL state. */
sealed interface AskUserState {
    data object Idle : AskUserState
    data class Open(val id: String, val question: String, val options: List<String>) : AskUserState
    data class Answered(val id: String, val answer: String) : AskUserState
    data class Dismissed(val id: String) : AskUserState
    data class TimedOut(val id: String) : AskUserState
}

/** Human control precedence: exactly one state instead of two independent booleans. */
enum class ControlLockState {
    UNLOCKED,
    PAUSED,
    HOLDING,
    PAUSED_AND_HOLDING,
    ;

    val paused: Boolean get() = this == PAUSED || this == PAUSED_AND_HOLDING
    val holding: Boolean get() = this == HOLDING || this == PAUSED_AND_HOLDING
}

/** Status-strip narration states (run phases plus short transitional narration states). */
enum class NarrationState(val wire: String) {
    IDLE("idle"),
    STARTING("starting"),
    PLANNING("planning"),
    ACTING("acting"),
    VERIFYING("verifying"),
    PAUSED("paused"),
    RESUMING("resuming"),
    STOPPING("stopping"),
    ERROR("error"),
    ;
}
