package com.aura.aura_ui.agent.llm

import com.aura.aura_ui.data.prefs.SecureKeyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One API key per endpoint. Keys never leave EncryptedSharedPreferences except as an
 * Authorization header on a request the user initiated.
 */
@Singleton
class ProviderKeyStore @Inject constructor(private val secure: SecureKeyStore) {

    fun keyFor(endpoint: LlmEndpoint): String? =
        if (endpoint.isBuiltIn) secure.providerKey(endpoint.id)
        else secure.customEndpointKey(endpoint.id) ?: secure.providerKey(endpoint.id)

    fun save(endpoint: LlmEndpoint, key: String) {
        if (endpoint.isBuiltIn) secure.setProviderKey(endpoint.id, key)
        else secure.setCustomEndpointKey(endpoint.id, key)
    }

    fun remove(endpoint: LlmEndpoint) {
        if (endpoint.isBuiltIn) secure.removeProviderKey(endpoint.id)
        else secure.removeProviderKey("custom_${endpoint.id}")
    }

    fun hint(endpoint: LlmEndpoint): String? =
        if (endpoint.isBuiltIn) secure.maskedProviderKey(endpoint.id)
        else secure.customEndpointKey(endpoint.id)?.let { "••••" }

    fun has(endpoint: LlmEndpoint): Boolean = !keyFor(endpoint).isNullOrBlank()

    var tavilyKey: String?
        get() = secure.tavilyApiKey
        set(value) { secure.tavilyApiKey = value }
}
