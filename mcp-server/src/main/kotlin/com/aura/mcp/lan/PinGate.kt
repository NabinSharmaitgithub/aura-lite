package com.aura.mcp.lan

import java.security.MessageDigest

/**
 * The pairing PIN shown in MCP Center, with single use and a lockout against guessing.
 *
 * The gate is a two-state machine ([PinGateState.Active] / [PinGateState.Locked]). A locked
 * state always carries a deadline and an escalation level, and the level survives expiry so a
 * repeated attacker cannot reset the backoff by waiting out the first lock.
 */
class PinGate(
    private val newPin: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    sealed interface Result {
        data object Accepted : Result
        data class Wrong(val triesLeft: Int) : Result
        data class Locked(val retryAfterMs: Long) : Result
    }

    @Volatile var onPinChanged: ((String) -> Unit)? = null

    private val lock = Any()
    private var current: String = newPin()
    private var gateState: PinGateState = PinGateState.Active()

    val pin: String get() = synchronized(lock) { current }

    /** Immutable diagnostics snapshot. */
    fun state(): PinGateState = synchronized(lock) { gateState }

    fun tryPin(candidate: String): Result {
        val result: Result
        val changed: String?
        synchronized(lock) {
            val now = clock()
            var state = gateState
            if (state is PinGateState.Locked) {
                if (now < state.until) return Result.Locked(state.until - now)
                // Lock expired: keep the escalation level, clear the failure count.
                state = PinGateState.Active(failures = 0, level = state.level)
                gateState = state
            }
            val active = state as PinGateState.Active
            if (MessageDigest.isEqual(candidate.toByteArray(), current.toByteArray())) {
                gateState = PinGateState.Active()
                current = newPin()
                result = Result.Accepted
                changed = current
            } else {
                val failures = active.failures + 1
                if (failures >= MAX_FAILURES) {
                    val level = active.level + 1
                    val lockMs = (BASE_LOCK_MS shl (level - 1).coerceAtMost(10)).coerceAtMost(MAX_LOCK_MS)
                    gateState = PinGateState.Locked(until = now + lockMs, level = level)
                    current = newPin()
                    result = Result.Locked(lockMs)
                    changed = current
                } else {
                    gateState = PinGateState.Active(failures = failures, level = active.level)
                    result = Result.Wrong(MAX_FAILURES - failures)
                    changed = null
                }
            }
        }
        changed?.let { onPinChanged?.invoke(it) }
        return result
    }

    companion object {
        const val MAX_FAILURES = 5
        const val BASE_LOCK_MS = 60_000L
        const val MAX_LOCK_MS = 15 * 60_000L
    }
}
