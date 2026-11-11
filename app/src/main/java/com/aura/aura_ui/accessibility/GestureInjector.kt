package com.aura.aura_ui.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.aura.mcp.bridge.ToolError
import com.aura.mcp.bridge.ToolResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.coroutines.resume

/**
 * Gesture and text injection. Every method observes the screen afterwards and reports whether
 * state actually changed, so the agent can verify rather than assume.
 */
class GestureInjector(
    private val service: AuraAccessibilityService,
    private val extractor: UiTreeExtractor,
) {

    suspend fun tap(
        index: Int?,
        bounds: List<Int>?,
        text: String?,
        x: Float?,
        y: Float?,
        longPress: Boolean,
    ): ToolResult {
        val target = resolve(index, bounds, text, x, y)
            ?: return ToolResult.fail(ToolError.notFound("No element matched that locator"))
        val before = currentSnapshot()
        val point = target
        val dispatched = dispatchTap(point.first, point.second, longPress)
        if (!dispatched) return ToolResult.fail(ToolError.internal("Gesture was cancelled"))
        delay(if (longPress) 550 else 280)
        val after = currentSnapshot()
        return ToolResult.ok(
            changed = after.hash != before.hash,
            observed = observed(after, "Tapped ${indexText(index, point)}"),
        )
    }

    suspend fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long): ToolResult {
        val before = currentSnapshot()
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(50, 3000)))
            .build()
        val dispatched = suspendCancellableCoroutine { cont ->
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = cont.resume(true)
                override fun onCancelled(gestureDescription: GestureDescription?) = cont.resume(false)
            }, null)
        }
        if (!dispatched) return ToolResult.fail(ToolError.internal("Gesture was cancelled"))
        delay(280)
        val after = currentSnapshot()
        return ToolResult.ok(changed = after.hash != before.hash, observed = observed(after, "Swiped"))
    }

    suspend fun scroll(direction: String, amount: Float?, index: Int?): ToolResult {
        val before = currentSnapshot()
        val root = service.currentRoot()
        val scrollable = findScrollable(root, index, direction)
        val forward = direction == "down" || direction == "right"
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val performed = scrollable?.performAction(action) ?: false
        if (!performed) {
            val metrics = service.resources.displayMetrics
            val cx = metrics.widthPixels / 2f
            val cy = metrics.heightPixels / 2f
            val distance = metrics.heightPixels * (amount ?: 0.55f).coerceIn(0.15f, 0.85f)
            val (fx, fy, tx, ty) = when (direction) {
                "up" -> listOf(cx, cy + distance / 2, cx, cy - distance / 2)
                "left" -> listOf(cx + distance / 2, cy, cx - distance / 2, cy)
                "right" -> listOf(cx - distance / 2, cy, cx + distance / 2, cy)
                else -> listOf(cx, cy - distance / 2, cx, cy + distance / 2)
            }
            val path = Path().apply { moveTo(fx, fy); lineTo(tx, ty) }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 260))
                .build()
            suspendCancellableCoroutine { cont ->
                service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) = cont.resume(true)
                    override fun onCancelled(gestureDescription: GestureDescription?) = cont.resume(false)
                }, null)
            }
        }
        delay(320)
        val after = currentSnapshot()
        return ToolResult.ok(changed = after.hash != before.hash, observed = observed(after, "Scrolled $direction"))
    }

    suspend fun setValue(index: Int?, text: String): ToolResult {
        val before = currentSnapshot()
        val root = service.currentRoot()
        val bounds = index?.let { ScreenState.snapshot?.byIndex(it)?.bounds }
        val node = findEditable(root, bounds, null)
            ?: return ToolResult.permission("No editable field on screen", "accessibility")
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) return ToolResult.fail(ToolError.unsupported("This field does not accept set_value; try type_text"))
        delay(240)
        val after = currentSnapshot()
        return ToolResult.ok(changed = after.hash != before.hash, observed = observed(after, "Set value"))
    }

    suspend fun typeText(text: String, index: Int?, submit: Boolean): ToolResult {
        val before = currentSnapshot()
        val root = service.currentRoot()
        val bounds = index?.let { ScreenState.snapshot?.byIndex(it)?.bounds }
        val node = findEditable(root, bounds, null)
            ?: return ToolResult.permission("No focused text field", "accessibility")
        val existing = node.text?.toString().orEmpty()
        val combined = if (existing.isBlank()) text else existing + text
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, combined)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) return ToolResult.fail(ToolError.unsupported("This field rejected text input"))
        if (submit) {
            delay(120)
            pressEnter()
        }
        delay(260)
        val after = currentSnapshot()
        return ToolResult.ok(changed = after.hash != before.hash, observed = observed(after, "Typed text"))
    }

    suspend fun pressKey(key: String): ToolResult {
        when (key.lowercase()) {
            "back" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "home" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            "recents" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            "enter" -> pressEnter()
            "escape" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "delete" -> deleteLastCharacter()
            "tab" -> Unit
            else -> return ToolResult.fail(ToolError.unsupported("Unknown key: $key"))
        }
        delay(220)
        val after = currentSnapshot()
        return ToolResult.ok(changed = true, observed = observed(after, "Pressed $key"))
    }

    private fun pressEnter() {
        val root = service.currentRoot() ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (Build.VERSION.SDK_INT >= 30) {
            focused?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }
    }

    private fun deleteLastCharacter() {
        val root = service.currentRoot() ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        val current = focused.text?.toString().orEmpty()
        if (current.isNotEmpty()) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, current.dropLast(1))
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }

    suspend fun waitFor(condition: String, timeoutMs: Long): ToolResult {
        val deadline = System.currentTimeMillis() + timeoutMs
        val started = System.currentTimeMillis()
        var snapshot = currentSnapshot()
        while (System.currentTimeMillis() < deadline) {
            snapshot = currentSnapshot()
            val satisfied = if (condition.equals("idle", ignoreCase = true)) {
                ScreenGeneration.current == snapshot.generation
            } else {
                snapshot.containsText(condition)
            }
            if (satisfied) {
                return ToolResult.ok(
                    observed = observed(snapshot, "Condition matched: $condition"),
                    meta = buildJsonObject { put("duration_ms", System.currentTimeMillis() - started) },
                )
            }
            delay(250)
        }
        return ToolResult.fail(
            ToolError.timeout("Timed out waiting for: $condition"),
            observed = extractor.toJson(snapshot),
        )
    }

    suspend fun verify(expectation: String, index: Int?): ToolResult {
        val snapshot = currentSnapshot()
        val textMatch = snapshot.containsText(expectation)
        val nodeMatch = index != null && snapshot.byIndex(index) != null
        val tokens = expectation.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 3 }
        val tokenHits = tokens.count { token -> snapshot.nodes.any { it.label.lowercase().contains(token) } }
        val matched = textMatch || nodeMatch || (tokens.isNotEmpty() && tokenHits >= (tokens.size + 1) / 2)
        return ToolResult.ok(
            changed = false,
            observed = buildJsonObject {
                put("matched", matched)
                put("expectation", expectation)
                put("summary", snapshot.describe())
                put("token_hits", tokenHits)
                put("tree", extractor.toJson(snapshot))
            },
        )
    }

    fun currentSnapshot(): ScreenSnapshot =
        extractor.extract(
            root = service.currentRoot(),
            generation = ScreenGeneration.current,
        )

    private fun observed(snapshot: ScreenSnapshot, summary: String): JsonObject = buildJsonObject {
        put("summary", summary)
        put("foreground", snapshot.packageName)
        put("screen_generation", snapshot.generation)
        put("changed", snapshot.changed)
        put("tree", extractor.toJson(snapshot))
    }

    private fun indexText(index: Int?, point: Pair<Float, Float>): String =
        if (index != null) "index $index" else "(${point.first.toInt()}, ${point.second.toInt()})"

    private fun resolve(
        index: Int?,
        bounds: List<Int>?,
        text: String?,
        x: Float?,
        y: Float?,
    ): Pair<Float, Float>? {
        index?.let { i ->
            ScreenState.snapshot?.byIndex(i)?.let { return it.bounds.center() }
        }
        if (bounds != null && bounds.size == 4) {
            val rect = Rect(bounds[0], bounds[1], bounds[2], bounds[3])
            return rect.center()
        }
        text?.let { query ->
            val snapshot = ScreenState.snapshot ?: currentSnapshot()
            extractor.findTarget(snapshot, query)?.let { return it.bounds.center() }
        }
        if (x != null && y != null) return x to y
        return null
    }

    private fun Rect.center(): Pair<Float, Float> = (left + right) / 2f to (top + bottom) / 2f

    private suspend fun dispatchTap(x: Float, y: Float, longPress: Boolean): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val duration = if (longPress) 650L else 60L
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        return suspendCancellableCoroutine { cont ->
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = cont.resume(true)
                override fun onCancelled(gestureDescription: GestureDescription?) = cont.resume(false)
            }, null)
        }
    }

    private fun findScrollable(root: AccessibilityNodeInfo?, index: Int?, direction: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val bounds = index?.let { ScreenState.snapshot?.byIndex(it)?.bounds }
        return findFirst(root, 0) { node ->
            node.isScrollable && (bounds == null || boundsOf(node) == bounds)
        }
    }

    private fun findEditable(root: AccessibilityNodeInfo?, bounds: Rect?, hint: String?): AccessibilityNodeInfo? {
        if (root == null) return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) return focused
        val first = findFirst(root, 0) { node ->
            node.isEditable && (bounds == null || boundsOf(node) == bounds)
        }
        if (first != null) return first
        if (bounds != null) {
            return findFirst(root, 0) { node -> node.isEditable && boundsOf(node) == bounds }
        }
        return findFirst(root, 0) { it.isEditable }
    }

    private fun findFirst(
        node: AccessibilityNodeInfo,
        depth: Int,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (depth > 28) return null
        if (runCatching { predicate(node) }.getOrDefault(false)) return node
        for (i in 0 until runCatching { node.childCount }.getOrDefault(0)) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            findFirst(child, depth + 1, predicate)?.let { return it }
        }
        return null
    }

    private fun boundsOf(node: AccessibilityNodeInfo): Rect = Rect().also { node.getBoundsInScreen(it) }
}
