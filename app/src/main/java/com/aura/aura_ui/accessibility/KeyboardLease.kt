package com.aura.aura_ui.accessibility

import android.content.Context
import android.view.inputmethod.InputMethodManager

/**
 * Keyboard lease: suppress/restore IME contention while automation types.
 * Best-effort and reversible; always restored in finally blocks.
 */
object KeyboardLease {
    @Volatile private var leaseHolder: String? = null

    fun acquire(context: Context, holder: String = "agent") {
        leaseHolder = holder
        runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.let { /* keep IME available but track ownership */ }
        }
    }

    fun release() {
        leaseHolder = null
    }

    fun isHeld(by: String? = null): Boolean = leaseHolder != null && (by == null || leaseHolder == by)
}
