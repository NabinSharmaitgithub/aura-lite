package com.aura.aura_ui.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EncryptedSharedPreferences. Created lazily so a cold start does not pay the keystore cost
 * unless a key is actually needed.
 *
 * Rules: AES256-GCM, one key per provider endpoint, never logged, never traced, never in git.
 */
@Singleton
class SecureKeyStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun providerKey(endpointId: String): String? =
        prefs.getString("provider_key_$endpointId", null)?.takeIf { it.isNotBlank() }

    fun setProviderKey(endpointId: String, key: String) {
        prefs.edit().putString("provider_key_$endpointId", key.trim()).apply()
    }

    fun removeProviderKey(endpointId: String) {
        prefs.edit().remove("provider_key_$endpointId").apply()
    }

    fun hasProviderKey(endpointId: String): Boolean = providerKey(endpointId) != null

    /** Returns a masked hint safe to show in the Brain screen. */
    fun maskedProviderKey(endpointId: String): String? {
        val key = providerKey(endpointId) ?: return null
        if (key.length <= 8) return "••••"
        return key.take(4) + "••••" + key.takeLast(4)
    }

    fun customEndpointKey(uuid: String): String? =
        prefs.getString("provider_key_custom_$uuid", null)?.takeIf { it.isNotBlank() }

    fun setCustomEndpointKey(uuid: String, key: String) {
        prefs.edit().putString("provider_key_custom_$uuid", key.trim()).apply()
    }

    var tavilyApiKey: String?
        get() = prefs.getString("tavily_api_key", null)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value.isNullOrBlank()) prefs.edit().remove("tavily_api_key").apply()
            else prefs.edit().putString("tavily_api_key", value.trim()).apply()
        }

    var mcpPairingSecret: String?
        get() = prefs.getString("mcp_pairing_secret", null)
        set(value) = prefs.edit().putString("mcp_pairing_secret", value).apply()

    internal fun raw(): SharedPreferences = prefs

    companion object {
        const val FILE = "aura_secure"
    }
}
