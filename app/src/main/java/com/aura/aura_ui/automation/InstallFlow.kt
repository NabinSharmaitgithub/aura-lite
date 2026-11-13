package com.aura.aura_ui.automation

import com.aura.aura_ui.state.InstallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide install hand-off state. The final install confirmation happens in the Play Store,
 * so this machine ends at [InstallState.LISTING_OPENED] (or an explicit failure).
 */
object InstallFlow {
    private val _state = MutableStateFlow(InstallState.IDLE)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    fun update(state: InstallState) {
        _state.value = state
    }
}
