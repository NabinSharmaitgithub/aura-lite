package com.aura.aura_ui.agent.ledger

/**
 * Typed reasons the run loop can stop. Mapping these explicitly keeps the termination report
 * honest (budget vs quota vs wall-clock vs failure) instead of collapsing everything to
 * "stopped".
 */
enum class BudgetStopKind {
    STEPS,
    TIME,
    REQUESTS,
    FAILURES,
}

/**
 * Simple run budget controls (steps, wall-clock, optional request/token estimates).
 * Used by the agent loop to stop gracefully and report partial progress.
 */
data class RunBudget(
    val maxSteps: Int = 30,
    val maxWallClockMs: Long = 10 * 60 * 1000L,
    val maxModelRequests: Int = 60,
    val maxConsecutiveFailures: Int = 4,
) {
    private val startedAt: Long = System.currentTimeMillis()
    @Volatile private var requests: Int = 0
    @Volatile private var consecutiveFailures: Int = 0

    fun onRequest() { requests++ }
    fun onSuccess() { consecutiveFailures = 0 }
    fun onFailure() { consecutiveFailures++ }

    fun elapsedMs(): Long = System.currentTimeMillis() - startedAt
    fun requests(): Int = requests

    fun stopKind(stepsUsed: Int): BudgetStopKind? = when {
        elapsedMs() >= maxWallClockMs -> BudgetStopKind.TIME
        requests >= maxModelRequests -> BudgetStopKind.REQUESTS
        consecutiveFailures >= maxConsecutiveFailures -> BudgetStopKind.FAILURES
        stepsUsed >= maxSteps -> BudgetStopKind.STEPS
        else -> null
    }

    fun reason(kind: BudgetStopKind): String = when (kind) {
        BudgetStopKind.STEPS -> "Step budget exhausted ($maxSteps)"
        BudgetStopKind.TIME -> "Time budget exhausted"
        BudgetStopKind.REQUESTS -> "Model request budget exhausted"
        BudgetStopKind.FAILURES -> "Too many consecutive failures"
    }

    fun shouldStop(stepsUsed: Int): String? = stopKind(stepsUsed)?.let(::reason)
}
