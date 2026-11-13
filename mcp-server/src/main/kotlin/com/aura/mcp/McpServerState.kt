package com.aura.mcp

/**
 * Canonical lifecycle of the optional LAN MCP server.
 *
 * Previously `isRunning` was inferred from two nullable transport fields (`server` and
 * `webRtcTransport`) plus a `running` boolean in health. This enum is now the source of truth.
 */
enum class McpServerState(val wire: String) {
    STOPPED("stopped"),
    STARTING("starting"),
    RUNNING_HTTP("running_http"),
    RUNNING_WEBRTC("running_webrtc"),
    RUNNING_BOTH("running_both"),
    STOPPING("stopping"),
    ERROR("error"),
    ;

    val isRunning: Boolean
        get() = this == RUNNING_HTTP || this == RUNNING_WEBRTC || this == RUNNING_BOTH

    val isStopped: Boolean
        get() = this == STOPPED || this == ERROR
}

sealed interface McpServerEvent {
    data object StartHttp : McpServerEvent
    data object HttpStarted : McpServerEvent
    data object StartWebRtc : McpServerEvent
    data object WebRtcStarted : McpServerEvent
    data object Stop : McpServerEvent
    data object Stopped : McpServerEvent
    data class Fail(val reason: String?) : McpServerEvent
}

object McpServerTransitions {

    /** Returns the next state, or throws when the requested transition is illegal. */
    fun next(current: McpServerState, event: McpServerEvent, httpRunning: Boolean, webrtcRunning: Boolean): McpServerState =
        when (event) {
            McpServerEvent.StartHttp -> when (current) {
                McpServerState.STOPPED, McpServerState.ERROR -> McpServerState.STARTING
                else -> illegal(current, event)
            }
            McpServerEvent.HttpStarted -> combine(httpRunning = true, webrtcRunning = webrtcRunning)
            McpServerEvent.StartWebRtc -> when (current) {
                McpServerState.STOPPED, McpServerState.ERROR, McpServerState.RUNNING_HTTP -> McpServerState.STARTING
                else -> illegal(current, event)
            }
            McpServerEvent.WebRtcStarted -> combine(httpRunning = httpRunning, webrtcRunning = true)
            McpServerEvent.Stop -> when (current) {
                McpServerState.STOPPED -> McpServerState.STOPPED
                else -> McpServerState.STOPPING
            }
            McpServerEvent.Stopped -> McpServerState.STOPPED
            is McpServerEvent.Fail -> McpServerState.ERROR
        }

    private fun combine(httpRunning: Boolean, webrtcRunning: Boolean): McpServerState = when {
        httpRunning && webrtcRunning -> McpServerState.RUNNING_BOTH
        httpRunning -> McpServerState.RUNNING_HTTP
        webrtcRunning -> McpServerState.RUNNING_WEBRTC
        else -> McpServerState.STOPPED
    }

    private fun illegal(current: McpServerState, event: McpServerEvent): Nothing =
        throw IllegalStateException("Illegal MCP server transition: $current --${event::class.simpleName}-->")
}
