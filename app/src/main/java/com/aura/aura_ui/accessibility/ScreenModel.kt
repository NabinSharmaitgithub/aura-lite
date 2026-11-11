package com.aura.aura_ui.accessibility

import android.graphics.Rect
import java.util.concurrent.atomic.AtomicInteger

data class ScreenNode(
    val index: Int,
    val text: String,
    val description: String,
    val className: String,
    val bounds: Rect,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val packageName: String,
) {
    val label: String get() = text.ifBlank { description }
}

data class ScreenSnapshot(
    val generation: Int,
    val packageName: String,
    val summary: String,
    val nodes: List<ScreenNode>,
    val hash: Int,
    val changed: Boolean = true,
    val at: Long = System.currentTimeMillis(),
) {
    fun byIndex(index: Int): ScreenNode? = nodes.firstOrNull { it.index == index }
    fun byText(query: String): ScreenNode? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        return nodes.firstOrNull { it.label.lowercase() == q }
            ?: nodes.firstOrNull { it.label.lowercase().contains(q) }
    }
    fun containsText(query: String): Boolean = byText(query) != null
    fun describe(): String = summary.ifBlank { packageName }
}

/** Monotonic screen generation; bumped on any accessibility content change. */
object ScreenGeneration {
    private val counter = AtomicInteger(0)
    val current: Int get() = counter.get()
    fun bump(): Int = counter.incrementAndGet()
    fun reset() = counter.set(0)
}

/** Last extracted snapshot, shared by read and act paths. Metadata only, no node references. */
object ScreenState {
    @Volatile var snapshot: ScreenSnapshot? = null
    @Volatile var lastPickedPackage: String? = null

    fun clear() {
        snapshot = null
        lastPickedPackage = null
    }
}
