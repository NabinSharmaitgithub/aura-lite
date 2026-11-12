package com.aura.mcp.bridge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class TrustedClient(
    val token: String,
    val label: String = "Unknown client",
    val host: String? = null,
    val platform: String? = null,
    val firstApprovedAt: Long = 0L,
    val lastConnectedAt: Long = 0L,
    val toolsets: List<String>? = null,
) {
    val enabledToolsets: Set<String> get() = (toolsets?.toSet() ?: setOf("CORE", "ASSISTANT", "BROWSER"))
    val tokenId: String get() = token.take(8)
}

class TrustedClientsStore(
    private val load: () -> String?,
    private val save: (String) -> Unit,
    private val loadLegacyTokens: () -> Set<String> = { emptySet() },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private val _clients = MutableStateFlow(readInitial())
    val clients: StateFlow<List<TrustedClient>> = _clients.asStateFlow()

    fun isTrusted(token: String): Boolean =
        token.isNotEmpty() && _clients.value.any { it.token == token }

    fun approve(token: String, label: String?, host: String?, platform: String?, nowMillis: Long) {
        if (token.isEmpty()) return
        synchronized(lock) {
            val others = _clients.value.filterNot { it.token == token }
            persist(
                others + TrustedClient(
                    token = token,
                    label = label?.takeIf { it.isNotBlank() } ?: "Unknown client",
                    host = host,
                    platform = platform,
                    firstApprovedAt = nowMillis,
                    lastConnectedAt = nowMillis,
                ),
            )
        }
    }

    fun touch(token: String, label: String?, host: String?, platform: String?, nowMillis: Long) {
        synchronized(lock) {
            val existing = _clients.value.firstOrNull { it.token == token } ?: return
            persist(
                _clients.value.map { client ->
                    if (client.token != token) client
                    else client.copy(
                        label = label?.takeIf { it.isNotBlank() } ?: client.label,
                        host = host ?: client.host,
                        platform = platform ?: client.platform,
                        lastConnectedAt = nowMillis,
                    )
                },
            )
        }
    }

    fun setToolsets(token: String, toolsets: Set<String>) {
        synchronized(lock) {
            persist(
                _clients.value.map { client ->
                    if (client.token != token) client
                    else client.copy(toolsets = (toolsets + "CORE").sorted())
                },
            )
        }
    }

    fun revoke(token: String) {
        synchronized(lock) { persist(_clients.value.filterNot { it.token == token }) }
    }

    fun revokeAll() {
        synchronized(lock) { persist(emptyList()) }
    }

    private fun persist(clients: List<TrustedClient>) {
        _clients.value = clients
        runCatching { save(json.encodeToString(clients)) }
    }

    private fun readInitial(): List<TrustedClient> {
        val raw = load()
        if (raw != null) {
            return runCatching { json.decodeFromString<List<TrustedClient>>(raw) }.getOrElse { emptyList() }
        }
        val legacy = runCatching { loadLegacyTokens() }.getOrElse { emptySet() }
        if (legacy.isEmpty()) return emptyList()
        val migrated = legacy.map { TrustedClient(token = it) }
        runCatching { save(json.encodeToString(migrated)) }
        return migrated
    }
}
