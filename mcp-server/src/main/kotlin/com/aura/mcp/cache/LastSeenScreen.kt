package com.aura.mcp.cache

import kotlinx.serialization.json.JsonObject

/** Tracks last seen screen generation/signature and lightweight metadata for diff/eviction logic. */
class LastSeenScreen {
    @Volatile var generation: Long = -1L
        private set
    @Volatile var signature: String? = null
        private set
    @Volatile var packageName: String? = null
        private set
    @Volatile var snapshot: JsonObject? = null
        private set

    fun update(generation: Long, signature: String?, packageName: String?, snapshot: JsonObject? = null) {
        this.generation = generation
        this.signature = signature
        this.packageName = packageName
        this.snapshot = snapshot
    }

    fun clear() {
        generation = -1L
        signature = null
        packageName = null
        snapshot = null
    }
}
