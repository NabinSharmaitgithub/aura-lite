package com.aura.mcp

import android.content.Context
import android.util.Log
import com.aura.mcp.audit.McpAuditLogger
import com.aura.mcp.bridge.TrustedClientsStore
import com.aura.mcp.bridge.WebRtcTransport
import com.aura.mcp.lan.MdnsAdvertiser
import com.aura.mcp.lan.PairingCrypto
import com.aura.mcp.lan.PairingPayload
import com.aura.mcp.lan.ServerIdentity
import com.aura.mcp.lan.TrustedDevice
import com.aura.mcp.lan.TrustedDeviceStore
import com.aura.mcp.server.HttpMcpServer
import com.aura.mcp.server.WebRtcJsonRpcBridge
import com.aura.mcp.tools.McpToolRegistry
import com.aura.mcp.tools.ToolCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the optional LAN server. Disabled by default; nothing binds a socket until the user
 * turns it on in Settings → Trusted devices.
 */
@Singleton
class McpServerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: McpToolRegistry,
) {
    private val audit = McpAuditLogger(File(context.filesDir, "mcp/audit.jsonl"))
    private val trustedDevices by lazy { TrustedDeviceStore(context) }
    private val pairing = PairingCrypto()
    private val mdns = MdnsAdvertiser(context)
    private var identity: ServerIdentity? = null
    private var server: HttpMcpServer? = null
    private var boundPort: Int = DEFAULT_PORT

    // WebRTC (aura-mcp-connect compatible) transport support
    private var webRtcTransport: WebRtcTransport? = null
    private var webRtcBridge: WebRtcJsonRpcBridge? = null
    private var webRtcScope: CoroutineScope? = null
    private val webRtcTrustedClients by lazy {
        TrustedClientsStore(
            load = { context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE).getString("webrtc_trusted_clients", null) },
            save = { json -> context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE).edit().putString("webrtc_trusted_clients", json).apply() },
            loadLegacyTokens = {
                context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE).getStringSet("webrtc_trusted_tokens", emptySet()) ?: emptySet()
            },
        )
    }

    private val _health = MutableStateFlow(
        McpServerHealth(toolCount = ToolCatalog.all.size, clientCount = 0),
    )
    val health: StateFlow<McpServerHealth> = _health.asStateFlow()

    @Volatile private var serverState: McpServerState = McpServerState.STOPPED
    private val stateLock = Any()

    /** Canonical server lifecycle state. */
    val state: McpServerState get() = serverState

    val isRunning: Boolean get() = serverState.isRunning
    val webRtcPin: String? get() = runCatching { /* exposed via status */ null }.getOrNull()

    fun start(port: Int = DEFAULT_PORT): McpServerHealth {
        synchronized(stateLock) {
            if (serverState.isRunning || serverState == McpServerState.STARTING) return _health.value
            serverState = McpServerState.STARTING
        }
        return runCatching {
            val id = identity ?: ServerIdentity.generate().also { identity = it }
            val instance = HttpMcpServer(
                registry = registry,
                trustedDevices = trustedDevices,
                pairing = pairing,
                identity = id,
                audit = audit,
                health = { _health.value },
            )
            instance.start(bindHost = "0.0.0.0", requestedPort = port)
            server = instance
            boundPort = instance.port
            mdns.advertise("AURA-${android.os.Build.MODEL}".replace(" ", "-"), boundPort)
            serverState = if (webRtcTransport != null) McpServerState.RUNNING_BOTH else McpServerState.RUNNING_HTTP
            McpServerHealth(
                running = true,
                host = localIpv4() ?: "0.0.0.0",
                port = boundPort,
                toolCount = ToolCatalog.all.size,
                clientCount = trustedDevices.list().size,
                startedAt = System.currentTimeMillis(),
                state = serverState.wire,
            ).also { _health.value = it }
        }.getOrElse { error ->
            serverState = if (webRtcTransport != null) McpServerState.RUNNING_WEBRTC else McpServerState.ERROR
            McpServerHealth(
                running = serverState.isRunning,
                toolCount = ToolCatalog.all.size,
                clientCount = trustedDevices.list().size,
                lastError = error.message,
                state = serverState.wire,
            ).also { _health.value = it }
        }
    }

    fun stop() {
        synchronized(stateLock) {
            if (serverState != McpServerState.STOPPED) serverState = McpServerState.STOPPING
        }
        runCatching { server?.stop() }
        server = null
        mdns.stop()
        stopWebRtcLocked()
        synchronized(stateLock) { serverState = McpServerState.STOPPED }
        _health.value = _health.value.copy(running = false, port = 0, state = McpServerState.STOPPED.wire)
    }

    /** Starts WebRTC LAN MCP transport compatible with aura-mcp-connect signaling/pairing. */
    fun startWebRtc(): String? {
        val httpRunning: Boolean
        synchronized(stateLock) {
            if (serverState == McpServerState.RUNNING_WEBRTC ||
                serverState == McpServerState.RUNNING_BOTH ||
                serverState == McpServerState.STARTING
            ) {
                return null
            }
            httpRunning = server != null
            serverState = McpServerState.STARTING
        }
        return runCatching {
            val transport = WebRtcTransport(context, trustedClients = webRtcTrustedClients)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val bridge = WebRtcJsonRpcBridge(transport, registry, scope)
            transport.onStatusChanged = { status ->
                _health.value = _health.value.copy(
                    running = true,
                    host = status.addresses.firstOrNull() ?: "LAN",
                    port = status.port,
                    clientCount = webRtcTrustedClients.clients.value.size,
                    toolCount = ToolCatalog.all.size,
                    state = serverState.wire,
                )
            }
            val status = transport.startListening()
            webRtcTransport = transport
            webRtcBridge = bridge
            webRtcScope = scope
            synchronized(stateLock) {
                serverState = if (httpRunning) McpServerState.RUNNING_BOTH else McpServerState.RUNNING_WEBRTC
            }
            _health.value = McpServerHealth(
                running = true,
                host = status.addresses.firstOrNull() ?: "LAN",
                port = status.port,
                toolCount = ToolCatalog.all.size,
                clientCount = webRtcTrustedClients.clients.value.size,
                startedAt = System.currentTimeMillis(),
                state = serverState.wire,
            )
            Log.i("McpServerController", "WebRTC MCP listening on ${status.port}, pin=${status.pin}")
            status.pin
        }.getOrElse { e ->
            Log.w("McpServerController", "Failed to start WebRTC MCP", e)
            synchronized(stateLock) {
                serverState = if (httpRunning) McpServerState.RUNNING_HTTP else McpServerState.ERROR
            }
            _health.value = _health.value.copy(
                running = httpRunning,
                lastError = e.message,
                state = serverState.wire,
            )
            null
        }
    }

    fun stopWebRtc() {
        stopWebRtcLocked()
        synchronized(stateLock) {
            serverState = if (server != null) McpServerState.RUNNING_HTTP else McpServerState.STOPPED
        }
        _health.value = _health.value.copy(state = serverState.wire)
    }

    private fun stopWebRtcLocked() {
        runCatching { webRtcBridge?.close() }
        runCatching { webRtcTransport?.close() }
        runCatching { webRtcScope?.cancel() }
        webRtcBridge = null
        webRtcTransport = null
        webRtcScope = null
    }

    fun webRtcTrustedClients() = webRtcTrustedClients.clients.value
    fun revokeWebRtcClient(token: String) {
        webRtcTrustedClients.revoke(token)
        _health.value = _health.value.copy(clientCount = trustedDevices.list().size + webRtcTrustedClients.clients.value.size)
    }

    fun pairingPayload(): PairingPayload? {
        val identity = identity ?: return null
        return PairingPayload(
            host = localIpv4() ?: "127.0.0.1",
            port = boundPort,
            fingerprint = identity.fingerprint,
            nonce = pairing.newNonce(),
        )
    }

    fun trustedDevices(): List<TrustedDevice> = trustedDevices.list()

    fun revoke(clientId: String) {
        trustedDevices.revoke(clientId)
        _health.value = _health.value.copy(clientCount = trustedDevices.list().size)
    }

    fun auditLines(limit: Int = 100): List<String> = audit.read(limit)

    private fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    companion object {
        const val DEFAULT_PORT = 47821
    }
}
