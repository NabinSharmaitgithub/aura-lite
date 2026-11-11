package com.aura.mcp.lan

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class TrustedDevice(
    val clientId: String,
    val name: String,
    val tokenHash: String,
    val pairedAt: Long,
    val lastSeenAt: Long,
    val scopes: List<String>,
    val canApproveAuto: Boolean,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("clientId", clientId)
        put("name", name)
        put("pairedAt", pairedAt)
        put("lastSeenAt", lastSeenAt)
        put("scopes", scopes.joinToString(","))
        put("canApproveAuto", canApproveAuto)
    }
}

/**
 * Trusted MCP clients. Only SHA-256 hashes of bearer tokens are persisted.
 */
class TrustedDeviceStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "aura_mcp_devices",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    @Synchronized
    fun list(): List<TrustedDevice> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            Json.parseToJsonElement(raw).jsonObject.entries.map { (_, value) ->
                val o = value.jsonObject
                TrustedDevice(
                    clientId = o["clientId"]?.jsonPrimitive?.content.orEmpty(),
                    name = o["name"]?.jsonPrimitive?.content.orEmpty(),
                    tokenHash = o["tokenHash"]?.jsonPrimitive?.content.orEmpty(),
                    pairedAt = o["pairedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    lastSeenAt = o["lastSeenAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                    scopes = o["scopes"]?.jsonPrimitive?.content?.split(",")?.filter { it.isNotBlank() }.orEmpty(),
                    canApproveAuto = o["canApproveAuto"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun findByToken(token: String): TrustedDevice? {
        val hash = sha256(token)
        return list().firstOrNull { it.tokenHash == hash }
    }

    @Synchronized
    fun add(name: String, token: String, scopes: List<String> = listOf("*")): TrustedDevice {
        val device = TrustedDevice(
            clientId = "cli_" + UUID.randomUUID().toString().take(8),
            name = name.ifBlank { "MCP client" },
            tokenHash = sha256(token),
            pairedAt = System.currentTimeMillis(),
            lastSeenAt = System.currentTimeMillis(),
            scopes = scopes,
            canApproveAuto = false,
        )
        val next = (list() + device).associateBy { it.clientId }
        persist(next)
        return device
    }

    @Synchronized
    fun revoke(clientId: String) {
        persist(list().filterNot { it.clientId == clientId }.associateBy { it.clientId })
    }

    @Synchronized
    fun touch(clientId: String) {
        val next = list().map { if (it.clientId == clientId) it.copy(lastSeenAt = System.currentTimeMillis()) else it }
        persist(next.associateBy { it.clientId })
    }

    private fun persist(devices: Map<String, TrustedDevice>) {
        val root = buildJsonObject {
            devices.forEach { (id, d) ->
                put(id, buildJsonObject {
                    put("clientId", d.clientId)
                    put("name", d.name)
                    put("tokenHash", d.tokenHash)
                    put("pairedAt", d.pairedAt)
                    put("lastSeenAt", d.lastSeenAt)
                    put("scopes", d.scopes.joinToString(","))
                    put("canApproveAuto", d.canApproveAuto)
                })
            }
        }
        prefs.edit().putString(KEY, root.toString()).apply()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY = "trusted_devices"
    }
}

data class PairingPayload(
    val host: String,
    val port: Int,
    val fingerprint: String,
    val nonce: String,
) {
    fun toQrString(): String = buildJsonObject {
        put("host", host)
        put("port", port)
        put("fingerprint", fingerprint)
        put("nonce", nonce)
    }.toString()

    companion object {
        fun parse(raw: String): PairingPayload? = runCatching {
            val o = Json.parseToJsonElement(raw).jsonObject
            PairingPayload(
                host = o.getValue("host").jsonPrimitive.content,
                port = o.getValue("port").jsonPrimitive.content.toInt(),
                fingerprint = o.getValue("fingerprint").jsonPrimitive.content,
                nonce = o.getValue("nonce").jsonPrimitive.content,
            )
        }.getOrNull()
    }
}

/** Ephemeral pairing material. A nonce is single-use and expires after two minutes. */
class PairingCrypto {
    private var nonce: String = ""
    private var issuedAt: Long = 0L

    @Synchronized
    fun newNonce(): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        issuedAt = System.currentTimeMillis()
        return nonce
    }

    @Synchronized
    fun verifyNonce(candidate: String): Boolean {
        val fresh = System.currentTimeMillis() - issuedAt < 120_000
        val matches = MessageDigest.isEqual(nonce.toByteArray(), candidate.toByteArray())
        if (matches && fresh) nonce = ""
        return matches && fresh
    }

    fun newToken(): String {
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
