package com.aura.mcp.cache

import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Caches actionable screen elements and coordinates generation/signature for stale-handle detection.
 * `som_id`/`index` values are resolved server-side by tools that act on UI targets.
 */
class PerceptionCache {
    data class Element(
        val somId: Int,
        val bounds: IntArray? = null,
        val label: String? = null,
        val packageName: String? = null,
        val meta: JsonObject? = null,
    )

    private val generation = AtomicLong(0)
    @Volatile private var signature: String? = null
    @Volatile private var foregroundPackage: String? = null
    private val elements = ConcurrentHashMap<Int, Element>()

    fun generation(): Long = generation.get()
    fun signature(): String? = signature
    fun foregroundPackage(): String? = foregroundPackage

    fun update(
        newElements: List<Element>,
        screenSignature: String? = null,
        pkg: String? = null,
        bumpGeneration: Boolean = true,
    ) {
        if (bumpGeneration) generation.incrementAndGet()
        signature = screenSignature ?: signature
        foregroundPackage = pkg ?: foregroundPackage
        elements.clear()
        newElements.forEach { elements[it.somId] = it }
    }

    fun invalidate(bumpGeneration: Boolean = true) {
        if (bumpGeneration) generation.incrementAndGet()
        signature = null
        elements.clear()
    }

    fun get(somId: Int): Element? = elements[somId]
    fun snapshot(): Map<Int, Element> = elements.toMap()
    fun size(): Int = elements.size
}
