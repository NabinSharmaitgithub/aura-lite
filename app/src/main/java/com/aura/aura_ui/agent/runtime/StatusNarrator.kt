package com.aura.aura_ui.agent.runtime

import com.aura.aura_ui.state.NarrationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

/** User-facing status narration stream for agent progress (status strip / overlays). */
object StatusNarrator {
    private val _status = MutableStateFlow(NarrationState.IDLE)
    /** Canonical narration state; the string [status] below is derived from it. */
    val state: StateFlow<NarrationState> = _status.asStateFlow()
    val status: StateFlow<String> = _status.map { it.wire }.stateIn(
        kotlinx.coroutines.MainScope(),
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        NarrationState.IDLE.wire,
    )

    private val _detail = MutableStateFlow<String?>(null)
    val detail: StateFlow<String?> = _detail.asStateFlow()

    fun update(state: NarrationState, detail: String? = null) {
        _status.value = state
        _detail.value = detail
    }

    /** String-based overload kept for callers that only know a free-form status word. */
    fun update(status: String, detail: String? = null) {
        _status.value = NarrationState.entries.firstOrNull { it.wire == status } ?: NarrationState.IDLE
        _detail.value = detail
    }

    fun clear() {
        _status.value = NarrationState.IDLE
        _detail.value = null
    }
}
