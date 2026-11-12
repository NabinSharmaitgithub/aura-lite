package com.aura.mcp.bridge

data class ClientInfo(
    val tokenId: String,
    val agentLabel: String,
    val remoteAddr: String? = null,
    val clientVersion: String? = null,
    val host: String? = null,
    val platform: String? = null,
    val transport: String = "WebRTC",
)

interface ConnectionTracker {
    fun onConnect(info: ClientInfo): String
    fun onActivity(sessionKey: String)
    fun onDisconnect(sessionKey: String)
}

object NoOpConnectionTracker : ConnectionTracker {
    override fun onConnect(info: ClientInfo): String = ""
    override fun onActivity(sessionKey: String) = Unit
    override fun onDisconnect(sessionKey: String) = Unit
}
