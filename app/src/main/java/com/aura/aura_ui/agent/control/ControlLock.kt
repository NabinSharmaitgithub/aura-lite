package com.aura.aura_ui.agent.control

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Human control precedence (pause/lock) and touch arbitration hooks.
 * When locked/paused by user, mutating automation should yield.
 */
object ControlLock {
    private val userPaused = AtomicBoolean(false)
    private val userHolding = AtomicBoolean(false)

    fun setUserPaused(paused: Boolean) { userPaused.set(paused) }
    fun isUserPaused(): Boolean = userPaused.get()

    fun setUserHolding(holding: Boolean) { userHolding.set(holding) }
    fun isUserHolding(): Boolean = userHolding.get()

    fun canAutomateMutations(): Boolean = !isUserPaused() && !isUserHolding()
}
