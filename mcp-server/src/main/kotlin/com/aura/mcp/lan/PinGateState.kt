package com.aura.mcp.lan

/**
 * Explicit PIN gate state. Replaces the `failures` + `lockLevel` + `lockedUntil` triple with a
 * sealed state that cannot represent "locked with no deadline" or a negative failure count.
 */
sealed interface PinGateState {
    data class Active(val failures: Int = 0, val level: Int = 0) : PinGateState
    data class Locked(val until: Long, val level: Int) : PinGateState

    val locked: Boolean get() = this is Locked
}
