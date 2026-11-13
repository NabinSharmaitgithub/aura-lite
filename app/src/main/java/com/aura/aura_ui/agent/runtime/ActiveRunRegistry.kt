package com.aura.aura_ui.agent.runtime

import com.aura.aura_ui.state.RunHandleState
import java.util.concurrent.ConcurrentHashMap

/** Tracks active/steerable runs and their control handles (pause/cancel/status). */
object ActiveRunRegistry {
    data class RunHandle(
        val id: String,
        val goal: String,
        @Volatile var state: RunHandleState = RunHandleState.RUNNING,
        val startedAt: Long = System.currentTimeMillis(),
    ) {
        val status: String get() = state.wire
        val paused: Boolean get() = state == RunHandleState.PAUSED
        val cancelled: Boolean get() = state == RunHandleState.CANCELLING
        val finished: Boolean get() = state == RunHandleState.FINISHED
    }

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

    fun update(
        id: String,
        status: String? = null,
        paused: Boolean? = null,
        cancelled: Boolean? = null,
        state: RunHandleState? = null,
    ) {
        runs[id]?.let { h ->
            h.state = state ?: when {
                cancelled == true -> RunHandleState.CANCELLING
                paused == true -> RunHandleState.PAUSED
                paused == false -> RunHandleState.RUNNING
                status != null -> RunHandleState.entries.firstOrNull { it.wire == status } ?: h.state
                else -> h.state
            }
        }
    }

    fun finish(id: String) {
        val h = runs[id] ?: return
        h.state = RunHandleState.FINISHED
        runs.remove(id)
        if (currentRunId == id) currentRunId = null
    }

    fun snapshot(): List<RunHandle> = runs.values.toList()
}
