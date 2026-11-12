package com.aura.aura_ui.agent.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User-facing status narration stream for agent progress (status strip / overlays). */
object StatusNarrator {
    private val _status = MutableStateFlow("idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _detail = MutableStateFlow<String?>(null)
    val detail: StateFlow<String?> = _detail.asStateFlow()

    fun update(status: String, detail: String? = null) {
        _status.value = status
        _detail.value = detail
    }

    fun clear() {
        _status.value = "idle"
        _detail.value = null
    }
}
