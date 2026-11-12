package com.aura.aura_ui.agent.ledger

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

    fun shouldStop(stepsUsed: Int): String? {
        if (stepsUsed >= maxSteps) return "Step budget exhausted ($maxSteps)"
        if (elapsedMs() >= maxWallClockMs) return "Time budget exhausted"
        if (requests >= maxModelRequests) return "Model request budget exhausted"
        if (consecutiveFailures >= maxConsecutiveFailures) return "Too many consecutive failures"
        return null
    }
}
