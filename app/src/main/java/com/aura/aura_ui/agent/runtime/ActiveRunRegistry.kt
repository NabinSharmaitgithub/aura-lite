package com.aura.aura_ui.agent.runtime

import java.util.concurrent.ConcurrentHashMap

/** Tracks active/steerable runs and their control handles (pause/cancel/status). */
object ActiveRunRegistry {
    data class RunHandle(
        val id: String,
        val goal: String,
        @Volatile var status: String = "running",
        @Volatile var paused: Boolean = false,
        @Volatile var cancelled: Boolean = false,
        val startedAt: Long = System.currentTimeMillis(),
    )

    private val runs = ConcurrentHashMap<String, RunHandle>()
    @Volatile var currentRunId: String? = null
        private set

    fun start(id: String, goal: String): RunHandle {
        val handle = RunHandle(id = id, goal = goal)
        runs[id] = handle
        currentRunId = id
        return handle
    }

    fun get(id: String): RunHandle? = runs[id]
    fun current(): RunHandle? = currentRunId?.let { runs[it] }

    fun update(id: String, status: String? = null, paused: Boolean? = null, cancelled: Boolean? = null) {
        runs[id]?.let { h ->
            status?.let { h.status = it }
            paused?.let { h.paused = it }
            cancelled?.let { h.cancelled = it }
        }
    }

    fun finish(id: String) {
        runs.remove(id)
        if (currentRunId == id) currentRunId = runs.keys.firstOrNull()
    }

    fun snapshot(): List<RunHandle> = runs.values.toList()
}
