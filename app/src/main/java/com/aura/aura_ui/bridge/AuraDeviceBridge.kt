package com.aura.aura_ui.bridge

import android.view.accessibility.AccessibilityEvent
import com.aura.aura_ui.accessibility.AuraAccessibilityService
import com.aura.aura_ui.accessibility.ScreenCaptureManager
import com.aura.aura_ui.accessibility.ScreenGeneration
import com.aura.aura_ui.accessibility.ScreenState
import com.aura.aura_ui.accessibility.SystemControlsHelper
import com.aura.aura_ui.agent.AgentRuntime
import com.aura.aura_ui.agent.research.WebSearchClient
import com.aura.aura_ui.automation.AppResolver
import com.aura.aura_ui.automation.DataTools
import com.aura.aura_ui.browser.BrowserController
import com.aura.aura_ui.data.prefs.AuraSettings
import com.aura.aura_ui.notifications.AuraNotificationListenerService
import com.aura.aura_ui.perception.PerceptionEngine
import com.aura.aura_ui.agent.strategy.PerceptionTiering
import com.aura.mcp.bridge.DeviceBridge
import com.aura.mcp.bridge.ToolError
import com.aura.mcp.bridge.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * App-side implementation of the single device tool contract. Each branch maps one tool to one
 * subsystem. Permission failures are returned as structured errors with the exact fix path —
 * never as crashes and never as silent no-ops.
 */
@Singleton
class AuraDeviceBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AuraSettings,
    private val capture: ScreenCaptureManager,
    private val system: SystemControlsHelper,
    private val apps: AppResolver,
    private val data: DataTools,
    private val perception: PerceptionEngine,
    private val search: WebSearchClient,
    private val b2: com.aura.aura_ui.b2.B2Service,
    private val askUsers: com.aura.aura_ui.agent.AskUserBroker,
    private val agentRuntime: Provider<AgentRuntime>,
) : DeviceBridge {

    override suspend fun execute(tool: String, args: JsonObject): ToolResult {
        return when (tool) {
            // ── UI ────────────────────────────────────────────────────────────────
            "read_screen" -> readScreen(args)
            "tap" -> withService { service ->
                service.gestures.tap(
                    index = JsonArgs.int(args, "index") ?: JsonArgs.int(args, "som_id"),
                    bounds = JsonArgs.intList(args, "bounds"),
                    text = JsonArgs.str(args, "text"),
                    x = JsonArgs.float(args, "x"),
                    y = JsonArgs.float(args, "y"),
                    longPress = JsonArgs.bool(args, "long_press"),
                )
            }
            "swipe" -> withService { service ->
                val fx = JsonArgs.float(args, "from_x")
                val fy = JsonArgs.float(args, "from_y")
                val tx = JsonArgs.float(args, "to_x")
                val ty = JsonArgs.float(args, "to_y")
                if (fx == null || fy == null || tx == null || ty == null) {
                    ToolResult.fail(ToolError("invalid_args", "from_x, from_y, to_x and to_y are required"))
                } else {
                    service.gestures.swipe(fx, fy, tx, ty, JsonArgs.long(args, "duration_ms", 300) ?: 300)
                }
            }
            "draw" -> withService { service ->
                val raw = args["strokes"] as? JsonArray
                val strokes = raw?.mapNotNull { stroke ->
                    (stroke as? JsonArray)?.mapNotNull { point ->
                        val pair = point as? JsonArray
                        val x = (pair?.getOrNull(0) as? JsonPrimitive)?.floatOrNull
                        val y = (pair?.getOrNull(1) as? JsonPrimitive)?.floatOrNull
                        if (x != null && y != null) x to y else null
                    }?.takeIf { it.size >= 2 }
                }.orEmpty()
                if (strokes.isEmpty()) {
                    ToolResult.fail(ToolError("invalid_args", "strokes must contain at least one stroke with 2+ [x,y] points"))
                } else {
                    service.gestures.draw(strokes)
                }
            }
            "scroll" -> withService { service ->
                service.gestures.scroll(
                    direction = JsonArgs.str(args, "direction") ?: "down",
                    amount = JsonArgs.float(args, "amount"),
                    index = JsonArgs.int(args, "index") ?: JsonArgs.int(args, "som_id"),
                )
            }
            "set_value" -> withService { service ->
                val text = JsonArgs.rawStr(args, "text")
                    ?: return@withService ToolResult.fail(ToolError("invalid_args", "text is required"))
                service.gestures.setValue(JsonArgs.int(args, "index") ?: JsonArgs.int(args, "som_id"), text)
            }
            "type_text" -> withService { service ->
                val text = JsonArgs.rawStr(args, "text")
                    ?: return@withService ToolResult.fail(ToolError("invalid_args", "text is required"))
                service.gestures.typeText(text, JsonArgs.int(args, "index") ?: JsonArgs.int(args, "som_id"), JsonArgs.bool(args, "submit"))
            }
            "press_key" -> withService { service ->
                val key = JsonArgs.str(args, "key")
                    ?: return@withService ToolResult.fail(ToolError("invalid_args", "key is required"))
                service.gestures.pressKey(key)
            }
            "press_enter" -> withService { it.gestures.pressKey("enter") }
            "wait_for" -> withService { service ->
                val condition = JsonArgs.str(args, "condition")
                    ?: return@withService ToolResult.fail(ToolError("invalid_args", "condition is required"))
                service.gestures.waitFor(condition, JsonArgs.long(args, "timeout_ms", 8000) ?: 8000)
            }
            "verify_action" -> withService { service ->
                val expectation = JsonArgs.str(args, "expectation")
                    ?: return@withService ToolResult.fail(ToolError("invalid_args", "expectation is required"))
                service.gestures.verify(expectation, JsonArgs.int(args, "index") ?: JsonArgs.int(args, "som_id"))
            }
            "run_steps" -> ToolResult.fail(
                ToolError.unsupported("run_steps is dispatched by the tool registry, not the device bridge"),
            )
            "get_screenshot" -> screenshot(args)
            "perceive_screen" -> perceive(args)
            "request_screen_capture_permission" -> requestCapture()

            // ── Apps ──────────────────────────────────────────────────────────────
            "launch_app" -> apps.launch(JsonArgs.str(args, "app_name"), JsonArgs.str(args, "package"))
            "install_app" -> apps.install(JsonArgs.str(args, "app_name"), JsonArgs.str(args, "package"))
            "lookup_app" -> {
                val query = JsonArgs.str(args, "query")
                    ?: return ToolResult.fail(ToolError("invalid_args", "query is required"))
                apps.lookupResult(query)
            }
            "open_deeplink" -> {
                val uri = JsonArgs.str(args, "uri")
                    ?: return ToolResult.fail(ToolError("invalid_args", "uri is required"))
                apps.openDeeplink(uri, JsonArgs.str(args, "package"))
            }
            "resolve_deeplink" -> {
                val uri = JsonArgs.str(args, "uri")
                    ?: return ToolResult.fail(ToolError("invalid_args", "uri is required"))
                apps.resolveDeeplink(uri)
            }
            "list_app_deeplinks" -> {
                val pkg = JsonArgs.str(args, "package")
                    ?: return ToolResult.fail(ToolError("invalid_args", "package is required"))
                apps.listAppDeeplinks(pkg)
            }

            // ── Data ──────────────────────────────────────────────────────────────
            "find_files" -> data.findFiles(
                query = JsonArgs.str(args, "query") ?: "",
                mime = JsonArgs.str(args, "mime"),
                limit = JsonArgs.int(args, "limit", 20) ?: 20,
            )
            "open_file" -> data.openFile(JsonArgs.str(args, "uri"), JsonArgs.str(args, "path"))
            "clipboard" -> data.clipboard(
                JsonArgs.str(args, "action") ?: "get",
                JsonArgs.rawStr(args, "text"),
            )
            "resolve_contact" -> data.resolveContact(
                query = JsonArgs.str(args, "query") ?: "",
                limit = JsonArgs.int(args, "limit", 5) ?: 5,
            )
            "dial" -> data.dial(
                number = JsonArgs.str(args, "number"),
                contact = JsonArgs.str(args, "contact"),
                directCall = JsonArgs.bool(args, "direct_call"),
            )
            "compose_sms" -> data.composeSms(
                to = JsonArgs.str(args, "to"),
                body = JsonArgs.rawStr(args, "body"),
                send = JsonArgs.bool(args, "send"),
            )
            "upload_file" -> b2.uploadLocalFile(
                path = JsonArgs.str(args, "path") ?: return ToolResult.fail(ToolError.notFound("path is required")),
                objectKey = JsonArgs.str(args, "object_key"),
                contentType = JsonArgs.str(args, "content_type"),
            ).fold(
                onSuccess = { (key, bytes) -> ToolResult.ok(changed = true, observed = buildJsonObject { put("object_key", key); put("bytes", bytes) }) },
                onFailure = { ToolResult.fail(ToolError.internal(it.message ?: "Upload failed")) },
            )
            "download_file" -> b2.downloadToFile(
                objectKey = JsonArgs.str(args, "object_key") ?: return ToolResult.fail(ToolError.notFound("object_key is required")),
                destination = JsonArgs.str(args, "destination"),
            ).fold(
                onSuccess = { file -> ToolResult.ok(changed = true, observed = buildJsonObject { put("destination", file.absolutePath) }) },
                onFailure = { ToolResult.fail(ToolError.internal(it.message ?: "Download failed")) },
            )

            // ── System ────────────────────────────────────────────────────────────
            "get_device_status" -> ToolResult.ok(observed = system.deviceStatus())
            "volume" -> system.changeVolume(
                streamName = JsonArgs.str(args, "stream") ?: "music",
                action = JsonArgs.str(args, "action") ?: "get",
                level = JsonArgs.int(args, "level"),
            )
            "system_intent" -> system.systemIntent(
                action = JsonArgs.str(args, "action") ?: "",
                value = JsonArgs.rawStr(args, "value"),
                packageName = JsonArgs.str(args, "package"),
            )
            "watch_device_events" -> watchEvents(args)

            // ── Media / notifications ─────────────────────────────────────────────
            "media_control" -> system.mediaControl(
                action = JsonArgs.str(args, "action") ?: "play_pause",
                packageName = JsonArgs.str(args, "package"),
            )
            "get_media_sessions" -> system.mediaSessions()
            "read_notifications" -> AuraNotificationListenerService.read(
                context,
                limit = JsonArgs.int(args, "limit", 20) ?: 20,
                packageName = JsonArgs.str(args, "package"),
            )
            "notification_action" -> AuraNotificationListenerService.act(
                context,
                key = JsonArgs.str(args, "key") ?: "",
                action = JsonArgs.str(args, "action") ?: "",
            )
            "dismiss_notification" -> AuraNotificationListenerService.dismiss(
                context,
                key = JsonArgs.str(args, "key") ?: "",
            )

            // ── Web / browser ─────────────────────────────────────────────────────
            "web_search" -> search.search(
                query = JsonArgs.str(args, "query") ?: "",
                maxResults = JsonArgs.int(args, "max_results", 5) ?: 5,
            )
            "browser_open" -> {
                val url = JsonArgs.str(args, "url") ?: return ToolResult.fail(ToolError("invalid_args", "url is required"))
                BrowserController.open(context, url).fold(
                    onSuccess = { title ->
                        ToolResult.ok(
                            changed = true,
                            observed = buildJsonObject {
                                put("url", url)
                                put("title", title)
                                put("summary", "Opened $url")
                            },
                        )
                    },
                    onFailure = { ToolResult.fail(ToolError.internal(it.message ?: "Browser failed to open")) },
                )
            }
            "browser_read" -> BrowserController.read(
                JsonArgs.str(args, "selector"),
                JsonArgs.int(args, "max_chars", 12000) ?: 12000,
            ).fold(
                onSuccess = { text -> ToolResult.ok(observed = buildJsonObject { put("text", text) }) },
                onFailure = { ToolResult.fail(ToolError.internal(it.message ?: "Read failed")) },
            )
            "browser_find" -> BrowserController.find(JsonArgs.str(args, "query") ?: "").fold(
                onSuccess = { matches ->
                    ToolResult.ok(observed = buildJsonObject {
                        put("matches", buildJsonArray { matches.forEach { add(it) } })
                    })
                },
                onFailure = { ToolResult.fail(ToolError.internal(it.message ?: "Find failed")) },
            )
            "browser_act" -> BrowserController.act(
                action = JsonArgs.str(args, "action") ?: "",
                selector = JsonArgs.str(args, "selector"),
                text = JsonArgs.rawStr(args, "text"),
                direction = JsonArgs.str(args, "direction"),
            ).fold(
                onSuccess = { result -> ToolResult.ok(changed = true, observed = buildJsonObject { put("result", result) }) },
                onFailure = { ToolResult.fail(ToolError.internal(it.message ?: "Browser action failed")) },
            )
            "browser_extract" -> BrowserController.extract(JsonArgs.str(args, "selector")).fold(
                onSuccess = { data -> ToolResult.ok(observed = data) },
                onFailure = { ToolResult.fail(ToolError.internal(it.message ?: "Extract failed")) },
            )
            "browser_tabs" -> {
                val action = JsonArgs.str(args, "action") ?: "list"
                if (action == "open") {
                    val url = JsonArgs.str(args, "url")
                        ?: return ToolResult.fail(ToolError("invalid_args", "url is required for open"))
                    BrowserController.open(context, url)
                }
                ToolResult.ok(
                    observed = buildJsonObject {
                        put("tabs", buildJsonArray { BrowserController.tabs().forEach { add(it) } })
                        put("count", BrowserController.tabs().size)
                    },
                )
            }
            "browser_wait" -> BrowserController.waitFor(
                JsonArgs.str(args, "condition") ?: "idle",
                JsonArgs.long(args, "timeout_ms", 10000) ?: 10000,
            ).fold(
                onSuccess = { matched -> ToolResult.ok(observed = buildJsonObject { put("matched", matched) }) },
                onFailure = { ToolResult.fail(ToolError.internal(it.message ?: "Wait failed")) },
            )
            "browser_screenshot" -> BrowserController.screenshot(context).fold(
                onSuccess = { file -> ToolResult.ok(observed = buildJsonObject { put("path", file.absolutePath) }) },
                onFailure = { ToolResult.fail(ToolError.internal(it.message ?: "Screenshot failed")) },
            )
            "browser_upload" -> ToolResult.fail(
                ToolError.unsupported("File uploads need the user to pick a file; use browser_handoff"),
            )
            "browser_handoff" -> BrowserController.handoff(context, JsonArgs.str(args, "reason")).fold(
                onSuccess = { ToolResult.ok(changed = true, observed = buildJsonObject { put("handoff", true) }) },
                onFailure = { ToolResult.fail(ToolError.internal(it.message ?: "Handoff failed")) },
            )
            "browser_close" -> {
                BrowserController.close()
                ToolResult.ok(changed = true)
            }

            "ask_user" -> {
                val question = JsonArgs.str(args, "question") ?: return ToolResult.fail(ToolError.notFound("question is required"))
                val options = (args["options"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                } ?: emptyList()
                val timeoutSec = (args["timeout_s"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: 300L
                val answer = askUsers.ask(question, options, timeoutSec)
                ToolResult.ok(observed = buildJsonObject {
                    put("answer", answer)
                    put("awaited", true)
                })
            }
            "ask_aura" -> {
                val goal = JsonArgs.str(args, "goal") ?: return ToolResult.fail(ToolError.notFound("goal is required"))
                val voice = JsonArgs.bool(args, "voice")
                val context = JsonArgs.str(args, "context")
                val delegatedGoal = if (context.isNullOrBlank()) goal else "$goal\n\nContext: $context"
                return runCatching {
                    val runtime = agentRuntime.get()
                    val wasRunning = runtime.isRunning
                    if (wasRunning) {
                        return@runCatching ToolResult.fail(
                            ToolError("busy", "AURA is already running another task; wait for it to finish."),
                        )
                    }
                    runtime.submit(delegatedGoal, voice)
                    // Wait briefly for the run to start, then for completion (bounded).
                    val startDeadline = System.currentTimeMillis() + 2_000
                    while (!runtime.isRunning && System.currentTimeMillis() < startDeadline) {
                        delay(50)
                    }
                    val endDeadline = System.currentTimeMillis() + 180_000
                    while (runtime.isRunning && System.currentTimeMillis() < endDeadline) {
                        delay(300)
                    }
                    val st = runtime.state.value
                    val completed = !runtime.isRunning
                    val ok = completed && (st.outcome == com.aura.aura_ui.data.session.SessionOutcome.SUCCESS || st.error == null)
                    ToolResult(
                        ok = ok || (completed && st.answer != null),
                        changed = completed,
                        observed = buildJsonObject {
                            put("handoff", if (completed) "completed" else "timeout")
                            put("goal", goal)
                            put("running", runtime.isRunning)
                            put("status", st.status)
                            put("outcome", st.outcome ?: "")
                            put("steps", st.stepCount)
                            put("answer", (st.answer ?: st.error ?: "").take(4000))
                        },
                        error = if (completed && st.error != null) ToolError.internal(st.error) else null,
                    )
                }.getOrElse { e ->
                    ToolResult.fail(ToolError.internal(e.message ?: "ask_aura handoff failed"))
                }
            }
            "end_session" -> ToolResult.ok(
                observed = buildJsonObject {
                    put("outcome", JsonArgs.str(args, "outcome") ?: "success")
                    put("summary", JsonArgs.str(args, "summary") ?: "")
                },
            )

            else -> ToolResult.fail(ToolError.notFound("Unknown tool: $tool"))
        }
    }

    private suspend fun readScreen(args: JsonObject): ToolResult {
        val service = AuraAccessibilityService.instance
            ?: return ToolResult.permission(
                "Accessibility is off. Turn it on to let AURA read the screen.",
                "settings/accessibility",
            )
        val snapshot = service.readScreen(
            includeBounds = JsonArgs.bool(args, "include_bounds", true),
            changesOnly = JsonArgs.bool(args, "changes_only"),
            maxNodes = JsonArgs.int(args, "max_nodes"),
        )
        val base = service.extractor.toJson(snapshot, JsonArgs.bool(args, "include_bounds", true))
        val interactive = snapshot.nodes.count { it.clickable || it.editable || it.scrollable }
        val labeled = snapshot.nodes.count { it.label.isNotBlank() }
        val tierDecision = PerceptionTiering.decide(
            PerceptionTiering.Signals(
                interactiveNodes = interactive,
                labeledNodes = labeled,
                totalNodes = snapshot.nodes.size,
                screenChanged = snapshot.changed,
            ),
        )
        val observed = buildJsonObject {
            base.forEach { (k, v) -> put(k, v) }
            put(
                "perception_tiering",
                buildJsonObject {
                    put("tier", tierDecision.tier.name.lowercase())
                    put("reason", tierDecision.reason)
                    put("interactive_nodes", interactive)
                    put("labeled_nodes", labeled)
                    put("total_nodes", snapshot.nodes.size)
                },
            )
        }
        return ToolResult.ok(changed = snapshot.changed, observed = observed)
    }

    private suspend fun screenshot(args: JsonObject): ToolResult {
        if (!capture.hasConsent()) {
            capture.requestConsent()
            return ToolResult.permission(
                "Screen capture consent is required. Approve the system dialog.",
                "screen_capture",
            )
        }
        return capture.captureToFile(JsonArgs.int(args, "max_dimension", 1080) ?: 1080).fold(
            onSuccess = { file ->
                ToolResult.ok(
                    observed = buildJsonObject {
                        put("path", file.absolutePath)
                        put("bytes", file.length())
                        put("generation", ScreenGeneration.current)
                    },
                )
            },
            onFailure = { ToolResult.fail(ToolError.internal(it.message ?: "Capture failed")) },
        )
    }

    private suspend fun perceive(args: JsonObject): ToolResult {
        if (!capture.hasConsent()) {
            capture.requestConsent()
            return ToolResult.permission(
                "Screen capture consent is required for perception.",
                "screen_capture",
            )
        }
        val file = capture.captureToFile(JsonArgs.int(args, "max_dimension", 1080) ?: 1080)
            .getOrElse { return ToolResult.fail(ToolError.internal(it.message ?: "Capture failed")) }
        val mode = JsonArgs.str(args, "mode") ?: "both"
        val result = runCatching { perception.perceive(file, mode) }
            .getOrElse { return ToolResult.fail(ToolError.internal(it.message ?: "Perception failed")) }
        val observed = buildJsonObject {
            result.forEach { (k, v) -> put(k, v) }
            put(
                "perception_tiering",
                buildJsonObject {
                    put("tier", "full")
                    put("reason", "explicit visual/perception request")
                },
            )
        }
        return ToolResult.ok(observed = observed)
    }

    private fun requestCapture(): ToolResult {
        val granted = capture.hasConsent()
        if (!granted) capture.requestConsent()
        return ToolResult.ok(
            changed = !granted,
            observed = buildJsonObject {
                put("requested", !granted)
                put("granted", granted)
                put("note", if (!granted) "Approve the system screen-capture dialog." else "Capture is ready.")
            },
        )
    }

    private suspend fun watchEvents(args: JsonObject): ToolResult {
        val service = AuraAccessibilityService.instance
            ?: return ToolResult.permission("Accessibility is off.", "settings/accessibility")
        val names = JsonArgs.stringList(args, "events")
        val wanted = names.mapNotNull { name ->
            when (name.lowercase()) {
                "window_state" -> AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                "window_content" -> AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                "click" -> AccessibilityEvent.TYPE_VIEW_CLICKED
                "focus" -> AccessibilityEvent.TYPE_VIEW_FOCUSED
                "text" -> AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                "scroll" -> AccessibilityEvent.TYPE_VIEW_SCROLLED
                else -> null
            }
        }.toSet()
        val duration = JsonArgs.long(args, "duration_ms", 5000) ?: 5000
        val collected = withTimeoutOrNull(duration) {
            service.signals
                .take(60)
                .toList()
                .filter { wanted.isEmpty() || it.type in wanted }
        }.orEmpty()
        return ToolResult.ok(
            observed = buildJsonObject {
                put(
                    "events",
                    buildJsonArray {
                        collected.forEach { signal ->
                            add(buildJsonObject {
                                put("type", signal.type)
                                put("package", signal.packageName)
                                put("class", signal.className)
                                put("text", signal.text)
                                put("at", signal.at)
                            })
                        }
                    },
                )
                put("count", collected.size)
            },
        )
    }

    private suspend fun withService(block: suspend (AuraAccessibilityService) -> ToolResult): ToolResult {
        val service = AuraAccessibilityService.instance
            ?: return ToolResult.permission(
                "Accessibility is off. Turn it on to let AURA tap for you.",
                "settings/accessibility",
            )
        return block(service)
    }

    override suspend fun foregroundPackage(): String? =
        AuraAccessibilityService.instance?.foregroundPackage() ?: ScreenState.lastPickedPackage

    override suspend fun startWatching(events: List<String>) = Unit

    override suspend fun stopWatching() = Unit
}
