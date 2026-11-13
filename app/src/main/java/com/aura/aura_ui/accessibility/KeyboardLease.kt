package com.aura.aura_ui.accessibility

import android.content.Context
import android.view.inputmethod.InputMethodManager
import com.aura.aura_ui.state.KeyboardLeaseState
import java.util.concurrent.atomic.AtomicReference

/**
 * Keyboard lease: suppress/restore IME contention while automation types.
 * Best-effort and reversible; always restored in finally blocks.
 *
 * A reference count prevents one gesture from releasing a lease another gesture still needs, and
 * [KeyboardLeaseState] is the single source of truth for the holder/owner pair.
 */
object KeyboardLease {
    private val state = AtomicReference(KeyboardLeaseState())

    fun acquire(context: Context, holder: String = "agent") {
        state.updateAndGet { current ->
            current.copy(holders = current.holders + 1, owner = current.owner ?: holder)
        }
        runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.let { /* keep IME available but track ownership */ }
        }
    }

    fun release() {
        state.updateAndGet { current ->
            if (current.holders <= 1) KeyboardLeaseState()
            else current.copy(holders = current.holders - 1)
        }
    }

    fun currentState(): KeyboardLeaseState = state.get()

    fun isHeld(by: String? = null): Boolean {
        val current = state.get()
        return current.held && (by == null || current.owner == by)
    }
}
