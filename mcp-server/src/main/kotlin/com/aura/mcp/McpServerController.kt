package com.aura.mcp

import android.content.Context
import com.aura.mcp.audit.McpAuditLogger
import com.aura.mcp.lan.MdnsAdvertiser
import com.aura.mcp.lan.PairingCrypto
import com.aura.mcp.lan.PairingPayload
import com.aura.mcp.lan.ServerIdentity
import com.aura.mcp.lan.TrustedDevice
import com.aura.mcp.lan.TrustedDeviceStore
import com.aura.mcp.server.HttpMcpServer
import com.aura.mcp.tools.McpToolRegistry
import com.aura.mcp.tools.ToolCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton
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

    private val _health = MutableStateFlow(
        McpServerHealth(toolCount = ToolCatalog.all.size, clientCount = 0),
    )
    val health: StateFlow<McpServerHealth> = _health.asStateFlow()

    val isRunning: Boolean get() = server != null

    fun start(port: Int = DEFAULT_PORT): McpServerHealth {
        if (server != null) return _health.value
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
            McpServerHealth(
                running = true,
                host = localIpv4() ?: "0.0.0.0",
                port = boundPort,
                toolCount = ToolCatalog.all.size,
                clientCount = trustedDevices.list().size,
                startedAt = System.currentTimeMillis(),
            ).also { _health.value = it }
        }.getOrElse { error ->
            McpServerHealth(
                running = false,
                toolCount = ToolCatalog.all.size,
                clientCount = trustedDevices.list().size,
                lastError = error.message,
            ).also { _health.value = it }
        }
    }

    fun stop() {
        runCatching { server?.stop() }
        server = null
        mdns.stop()
        _health.value = _health.value.copy(running = false, port = 0)
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
