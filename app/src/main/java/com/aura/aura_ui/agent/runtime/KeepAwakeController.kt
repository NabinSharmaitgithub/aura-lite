package com.aura.aura_ui.agent.runtime

import android.content.Context
import android.os.PowerManager

/** Keeps device/CPU awake during active agent runs (best-effort). */
class KeepAwakeController(context: Context) {
    private val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(tag: String = "aura:agent-run") {
        if (wakeLock?.isHeld == true) return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply {
            setReferenceCounted(false)
            runCatching { acquire(10 * 60 * 1000L) }
        }
    }

    @Synchronized
    fun release() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }
}
