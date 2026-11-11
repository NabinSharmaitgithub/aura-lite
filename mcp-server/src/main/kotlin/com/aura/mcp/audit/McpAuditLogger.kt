package com.aura.mcp.audit

import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Append-only audit log. Arguments are hashed here; the unhashed arguments live only in the
 * user-deletable session log.
 */
class McpAuditLogger(private val file: File) {

    init {
        file.parentFile?.mkdirs()
    }

    @Synchronized
    fun record(
        clientId: String,
        tool: String,
        args: JsonObject,
        ok: Boolean,
        durationMs: Long,
        approvedBy: String? = null,
        blocked: Boolean = false,
    ) {
        val line = buildJsonObject {
            put("t", System.currentTimeMillis())
            put("client", clientId)
            put("tool", tool)
            put("args_hash", sha256(args.toString()))
            put("ok", ok)
            put("duration_ms", durationMs)
            put("approved_by", approvedBy)
            put("blocked", blocked)
        }
        runCatching { file.appendText(line.toString() + "\n") }
    }

    fun read(limit: Int = 200): List<String> {
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.isNotBlank() }.takeLast(limit)
    }

    fun prune(olderThanMs: Long): Int {
        if (!file.exists()) return 0
        val cutoff = Instant.now().toEpochMilli() - olderThanMs
        val kept = file.readLines().filter { line ->
            val ts = Regex("\"t\":(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull()
            ts != null && ts >= cutoff
        }
        file.writeText(kept.joinToString("\n", postfix = if (kept.isEmpty()) "" else "\n"))
        return kept.size
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
}
