package com.aura.aura_ui.agent.control

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.aura.aura_ui.state.ControlLockState

/**
 * Human control precedence (pause/lock) and touch arbitration hooks.
 * When locked/paused by user, mutating automation should yield.
 *
 * The old implementation used two independent [AtomicBoolean]s, so it could be paused while
 * releasing a hold and briefly produce contradictory snapshots. A single atomic [ControlLockState]
 * is now authoritative; the legacy booleans are derived from it.
 */
object ControlLock {
    private val state = AtomicReference(ControlLockState.UNLOCKED)
    private val lastChangedAt = AtomicLong(System.currentTimeMillis())
    @Volatile private var lastReason: String? = null
    @Volatile private var changedBy: String = "system"

    /** Immutable diagnostics snapshot for the debug cockpit and control-lock diagnostics. */
    data class Diagnostics(
        val state: ControlLockState,
        val paused: Boolean,
        val holding: Boolean,
        val canAutomateMutations: Boolean,
        val lastChangedAt: Long,
        val lastReason: String?,
        val changedBy: String,
    )

    fun setUserPaused(paused: Boolean) {
        val previous = state.getAndUpdate { current ->
            when {
                paused && current.holding -> ControlLockState.PAUSED_AND_HOLDING
                paused -> ControlLockState.PAUSED
                current.holding -> ControlLockState.HOLDING
                else -> ControlLockState.UNLOCKED
            }
        }
        if (previous.paused != paused) mark("setUserPaused", if (paused) "paused" else "resumed")
    }

    fun isUserPaused(): Boolean = state.get().paused

    fun setUserHolding(holding: Boolean) {
        val previous = state.getAndUpdate { current ->
            when {
                holding && current.paused -> ControlLockState.PAUSED_AND_HOLDING
                holding -> ControlLockState.HOLDING
                current.paused -> ControlLockState.PAUSED
                else -> ControlLockState.UNLOCKED
            }
        }
        if (previous.holding != holding) mark("setUserHolding", if (holding) "holding" else "released")
    }

    fun isUserHolding(): Boolean = state.get().holding

    fun currentState(): ControlLockState = state.get()

    fun canAutomateMutations(): Boolean = state.get() == ControlLockState.UNLOCKED

    private fun mark(by: String, reason: String) {
        changedBy = by
        lastReason = reason
        lastChangedAt.set(System.currentTimeMillis())
    }

    fun diagnostics(): Diagnostics {
        val current = state.get()
        return Diagnostics(
            state = current,
            paused = current.paused,
            holding = current.holding,
            canAutomateMutations = current == ControlLockState.UNLOCKED,
            lastChangedAt = lastChangedAt.get(),
            lastReason = lastReason,
            changedBy = changedBy,
        )
    }

    fun diagnosticsJson(): JsonObject = diagnostics().let { d ->
        buildJsonObject {
            put("state", d.state.name)
            put("paused", d.paused)
            put("holding", d.holding)
            put("can_automate_mutations", d.canAutomateMutations)
            put("last_changed_at", d.lastChangedAt)
            put("last_reason", d.lastReason ?: "")
            put("changed_by", d.changedBy)
        }
    }

    fun resetDiagnostics() {
        lastReason = null
        changedBy = "system"
        lastChangedAt.set(System.currentTimeMillis())
    }
}
