package com.aura.aura_ui.agent.runtime

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Run lifecycle hooks (start/end) for observability, notifications, learnings dispatch and
 * future remote/telemetry sinks. Listener failures are always contained.
 */
object RunLifecycleHooks {
    interface Listener {
        fun onRunStart(sessionId: String?, goal: String) {}
        fun onRunEnd(sessionId: String?, outcome: String?, steps: Int, answer: String?) {}
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    fun add(listener: Listener): Listener {
        listeners.addIfAbsent(listener)
        return listener
    }

    fun remove(listener: Listener) {
        listeners.remove(listener)
    }

    fun start(sessionId: String?, goal: String) {
        listeners.forEach { runCatching { it.onRunStart(sessionId, goal) } }
    }

    fun end(sessionId: String?, outcome: String?, steps: Int, answer: String?) {
        listeners.forEach { runCatching { it.onRunEnd(sessionId, outcome, steps, answer) } }
    }
}
