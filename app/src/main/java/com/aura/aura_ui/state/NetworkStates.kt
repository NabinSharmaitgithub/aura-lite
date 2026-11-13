package com.aura.aura_ui.state

/** Firebase sync lifecycle, one value instead of a bag of lists. */
enum class SyncState {
    IDLE,
    UNAVAILABLE,
    NOT_CONFIGURED,
    UNAUTHENTICATED,
    SYNCING,
    OK,
    PARTIAL,
    ERROR,
}

/** Eval run lifecycle, from selection to a recorded verdict. */
sealed interface EvalRunState {
    data object Idle : EvalRunState
    data class Submitted(val taskId: String, val goal: String) : EvalRunState
    data class Running(val taskId: String) : EvalRunState
    data class Finished(val taskId: String, val verdict: String, val steps: Int) : EvalRunState
    data class Failed(val taskId: String, val reason: String) : EvalRunState
}

/** Gemini Live websocket lifecycle. */
enum class LiveSessionState {
    CLOSED,
    CONNECTING,
    CONNECTED,
    READY,
    TURN_COMPLETE,
    CLOSING,
    FAILED,
    ;

    val canSend: Boolean get() = this == CONNECTED || this == READY || this == TURN_COMPLETE
}

/** Remote B2 backup lifecycle. */
enum class BackupState {
    IDLE,
    QUEUED,
    UPLOADING,
    UPLOADED,
    SKIPPED,
    FAILED,
}

/** Session trace lifecycle. */
enum class SessionLifecycle {
    ACTIVE,
    ENDED,
    ERROR,
}

/** Checkpoint availability for resume. */
sealed interface CheckpointAvailability {
    data object None : CheckpointAvailability
    data class Available(val sessionId: String, val goal: String, val stepCount: Int) : CheckpointAvailability
}

/** Onboarding steps, named instead of bare integers. */
enum class OnboardingStep(val index: Int) {
    WELCOME(0),
    DEAL(1),
    NAME(2),
    BRAIN(3),
    ACCESSIBILITY(4),
    OVERLAY(5),
    NOTIFICATIONS(6),
    MICROPHONE(7),
    VOICE(8),
    DONE(9),
    ;

    val isPermissionStep: Boolean
        get() = this == ACCESSIBILITY || this == OVERLAY || this == NOTIFICATIONS || this == MICROPHONE

    fun next(): OnboardingStep = entries.firstOrNull { it.index == index + 1 } ?: DONE

    fun previous(): OnboardingStep = entries.firstOrNull { it.index == index - 1 } ?: WELCOME

    companion object {
        fun fromIndex(index: Int): OnboardingStep =
            entries.firstOrNull { it.index == index } ?: WELCOME

        fun clamp(index: Int): Int = index.coerceIn(WELCOME.index, DONE.index)
    }
}

/** Lifecycle of a tracked run handle in ActiveRunRegistry. */
enum class RunHandleState(val wire: String) {
    RUNNING("running"),
    PAUSED("paused"),
    CANCELLING("cancelling"),
    FINISHED("finished"),
    ;
}
