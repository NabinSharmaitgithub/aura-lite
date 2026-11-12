package com.aura.mcp.lan

import java.security.MessageDigest

/**
 * The pairing PIN shown in MCP Center, with single use and a lockout against guessing.
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
    private var failures = 0
    private var lockLevel = 0
    private var lockedUntil = 0L

    val pin: String get() = synchronized(lock) { current }

    fun tryPin(candidate: String): Result {
        val result: Result
        val changed: String?
        synchronized(lock) {
            val now = clock()
            if (now < lockedUntil) return Result.Locked(lockedUntil - now)
            if (MessageDigest.isEqual(candidate.toByteArray(), current.toByteArray())) {
                failures = 0
                lockLevel = 0
                current = newPin()
                result = Result.Accepted
                changed = current
            } else if (++failures >= MAX_FAILURES) {
                failures = 0
                lockLevel++
                val lockMs = (BASE_LOCK_MS shl (lockLevel - 1).coerceAtMost(10)).coerceAtMost(MAX_LOCK_MS)
                lockedUntil = now + lockMs
                current = newPin()
                result = Result.Locked(lockMs)
                changed = current
            } else {
                result = Result.Wrong(MAX_FAILURES - failures)
                changed = null
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
