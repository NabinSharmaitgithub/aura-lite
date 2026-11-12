package com.aura.aura_ui.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Compact indexed accessibility tree. Bounded depth and node count keep memory flat; a hash of
 * the visible text makes `changes_only` cheap and prevents acting on a stale screen.
 */
class UiTreeExtractor(
    private val maxNodes: Int = 150,
    private val maxDepth: Int = 28,
) {

    fun extract(
        root: AccessibilityNodeInfo?,
        generation: Int = ScreenGeneration.current,
        includeBounds: Boolean = true,
        changesOnly: Boolean = false,
        maxNodesOverride: Int? = null,
    ): ScreenSnapshot {
        if (root == null) {
            return ScreenSnapshot(generation, "", "", emptyList(), 0)
        }
        root.refresh()
        val limit = (maxNodesOverride ?: maxNodes).coerceIn(10, 400)
        val collected = ArrayList<ScreenNode>(minOf(limit, 96))
        val hash = StringBuilder()
        walk(root, 0, 0, limit, includeBounds, collected, hash)
        val digest = hash.toString().hashCode()
        val previous = ScreenState.snapshot
        val changed = previous == null || previous.hash != digest
        val packageName = root.packageName?.toString().orEmpty()
        val summary = buildString {
            if (packageName.isNotBlank()) append(packageName.substringAfterLast('.'))
            val titleNode = collected.firstOrNull { it.text.isNotBlank() && it.index <= 2 }
            if (titleNode != null) {
                if (isNotEmpty()) append(" · ")
                append(titleNode.label.take(48))
            }
        }
        val nodes = if (changesOnly && previous != null && previous.hash == digest) {
            emptyList()
        } else {
            collected
        }
        val snapshot = ScreenSnapshot(generation, packageName, summary, nodes, digest, changed)
        ScreenState.snapshot = snapshot
        return snapshot
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        counter: Int,
        limit: Int,
        includeBounds: Boolean,
        out: MutableList<ScreenNode>,
        hash: StringBuilder,
    ): Int {
        if (depth > maxDepth || out.size >= limit) return counter
        val visible = runCatching { node.isVisibleToUser }.getOrDefault(true)
        val rect = Rect().also { node.getBoundsInScreen(it) }
        val text = node.text?.toString()?.trim()?.take(240).orEmpty()
        val desc = node.contentDescription?.toString()?.trim()?.take(240).orEmpty()
        val clickable = node.isClickable
        val editable = node.isEditable
        val scrollable = node.isScrollable
        val interesting = visible && (text.isNotBlank() || desc.isNotBlank() || clickable || editable || scrollable)

        var next = counter
        var index = -1
        if (interesting) {
            index = next
            next += 1
            val className = node.className?.toString()?.substringAfterLast('.').orEmpty()
            out += ScreenNode(
                index = index,
                text = text,
                description = desc,
                className = className,
                bounds = rect,
                clickable = clickable,
                editable = editable,
                scrollable = scrollable,
                checkable = runCatching { node.isCheckable }.getOrDefault(false),
                checked = runCatching { node.isChecked }.getOrDefault(false),
                packageName = node.packageName?.toString().orEmpty(),
            )
            hash.append(index).append(text).append('|').append(desc).append('|')
            hash.append(rect.flattenToString()).append('|').append(if (clickable) 1 else 0)
            hash.append(if (editable) 1 else 0).append('\n')
        }
        if (out.size >= limit) return next
        for (i in 0 until runCatching { node.childCount }.getOrDefault(0)) {
            if (out.size >= limit) break
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            next = walk(child, depth + 1, next, limit, includeBounds, out, hash)
        }
        return next
    }

    fun toJson(snapshot: ScreenSnapshot, includeBounds: Boolean = true): JsonObject {
        val nodesArray = buildJsonArray {
                snapshot.nodes.forEach { node ->
                    add(
                        buildJsonObject {
                            put("i", node.index)
                            put("som_id", node.index) // alias for AURA-style grounding
                            if (node.text.isNotBlank()) put("text", node.text)
                            if (node.description.isNotBlank()) put("desc", node.description)
                            if (node.className.isNotBlank()) put("class", node.className)
                            if (includeBounds) {
                                put(
                                    "bounds",
                                    buildJsonArray {
                                        add(node.bounds.left)
                                        add(node.bounds.top)
                                        add(node.bounds.right)
                                        add(node.bounds.bottom)
                                    },
                                )
                            }
                            if (node.clickable) put("clickable", true)
                            if (node.editable) put("editable", true)
                            if (node.scrollable) put("scrollable", true)
                            if (node.checkable) {
                                put("checkable", true)
                                put("checked", node.checked)
                            }
                        },
                    )
                }
            }
        return buildJsonObject {
        put("package", snapshot.packageName)
        put("summary", snapshot.summary)
        put("generation", snapshot.generation)
        put("screen_generation", snapshot.generation)
        put("screen_signature", snapshot.hash.toString())
        put("changed", snapshot.changed)
        put("node_count", snapshot.nodes.size)
        put("nodes", nodesArray)
        // compact list alias for consumers expecting element arrays
        put("elements", nodesArray)
    }
    }

    fun stableKey(snapshot: ScreenSnapshot): String =
        MessageDigest.getInstance("SHA-256")
            .digest(snapshot.hash.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(12)

    /** Text flattened for wait_for / verify_action. */
    fun flatten(snapshot: ScreenSnapshot): String =
        snapshot.nodes.joinToString(" \n ") { it.label }.lowercase()

    /** Find the best clickable node for a query, preferring exact matches. */
    fun findTarget(snapshot: ScreenSnapshot, query: String): ScreenNode? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        val pool = snapshot.nodes.filter { it.label.isNotBlank() }
        return pool.firstOrNull { it.label.lowercase() == q && it.clickable }
            ?: pool.firstOrNull { it.label.lowercase() == q }
            ?: pool.firstOrNull { it.label.lowercase().contains(q) && it.clickable }
            ?: pool.firstOrNull { it.label.lowercase().contains(q) }
    }
}
