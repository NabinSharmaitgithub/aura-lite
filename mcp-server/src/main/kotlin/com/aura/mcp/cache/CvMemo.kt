package com.aura.mcp.cache

import java.util.concurrent.ConcurrentHashMap

/** Memoizes CV/perception results keyed by screen signature/hash to avoid redundant work. */
class CvMemo {
    private val map = ConcurrentHashMap<String, Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = map[key] as? T
    fun put(key: String, value: Any?) { map[key] = value }
    fun clear() = map.clear()
    fun remove(key: String) { map.remove(key) }
}
