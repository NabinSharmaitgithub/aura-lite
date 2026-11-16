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

    /** Backblaze B2 application key id (stored encrypted; the application key lives here too). */
    var b2KeyId: String?
        get() = prefs.getString("b2_key_id", null)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value.isNullOrBlank()) prefs.edit().remove("b2_key_id").apply()
            else prefs.edit().putString("b2_key_id", value.trim()).apply()
        }

    /** Backblaze B2 application key (the secret half). Never shown again; only masked hints. */
    var b2ApplicationKey: String?
        get() = prefs.getString("b2_application_key", null)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value.isNullOrBlank()) prefs.edit().remove("b2_application_key").apply()
            else prefs.edit().putString("b2_application_key", value.trim()).apply()
        }

    fun hasB2Credentials(): Boolean = b2KeyId != null && b2ApplicationKey != null

    /** Masked hint safe to show in Settings. Never reveals the middle of the key. */
    fun maskedB2ApplicationKey(): String? {
        val key = b2ApplicationKey ?: return null
        if (key.length <= 8) return "••••"
        return key.take(4) + "••••" + key.takeLast(4)
    }

    // ── Credential & Unlock Vault ─────────────────────────────────────────────

    var phoneUnlockType: String
        get() = prefs.getString("phone_unlock_type", "none") ?: "none"
        set(value) = prefs.edit().putString("phone_unlock_type", value.trim().lowercase()).apply()

    var phoneUnlockSecret: String?
        get() = prefs.getString("phone_unlock_secret", null)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value.isNullOrBlank()) prefs.edit().remove("phone_unlock_secret").apply()
            else prefs.edit().putString("phone_unlock_secret", value.trim()).apply()
        }

    fun hasPhoneUnlock(): Boolean = !phoneUnlockSecret.isNullOrBlank()

    fun savePhoneUnlock(type: String, secret: String) {
        phoneUnlockType = type
        phoneUnlockSecret = secret
    }

    fun clearPhoneUnlock() {
        prefs.edit().remove("phone_unlock_type").remove("phone_unlock_secret").apply()
    }

    fun getVaultItems(): List<VaultItem> {
        val raw = prefs.getString("vault_items_json", null) ?: return emptyList()
        return runCatching {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            json.decodeFromString<List<VaultItem>>(raw)
        }.getOrDefault(emptyList())
    }

    fun saveVaultItem(item: VaultItem) {
        val current = getVaultItems().toMutableList()
        val index = current.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            current[index] = item
        } else {
            current.add(0, item)
        }
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        prefs.edit().putString("vault_items_json", json.encodeToString(current)).apply()
    }

    fun removeVaultItem(id: String) {
        val current = getVaultItems().filter { it.id != id }
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        prefs.edit().putString("vault_items_json", json.encodeToString(current)).apply()
    }

    fun findVaultItem(service: String): VaultItem? {
        val lower = service.trim().lowercase()
        return getVaultItems().firstOrNull {
            it.service.lowercase().contains(lower) || lower.contains(it.service.lowercase())
        }
    }

    internal fun raw(): SharedPreferences = prefs

    companion object {
        const val FILE = "aura_secure"
    }
}

@kotlinx.serialization.Serializable
data class VaultItem(
    val id: String = java.util.UUID.randomUUID().toString().take(8),
    val service: String,
    val category: String = "App", // App, Passkey, Notes, PIN, Other
    val username: String = "",
    val secret: String,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)
