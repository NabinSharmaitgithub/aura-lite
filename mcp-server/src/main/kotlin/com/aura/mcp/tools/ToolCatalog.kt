package com.aura.mcp.tools

import com.aura.mcp.bridge.ToolDefinition
import com.aura.mcp.bridge.ToolDomain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * The single device tool surface. Both the in-app agent and paired MCP clients read these
 * definitions, so the schema is authored exactly once.
 */
object ToolCatalog {

    private fun str(description: String, default: String? = null, enum: List<String>? = null) = buildJsonObject {
        put("type", "string")
        put("description", description)
        if (default != null) put("default", default)
        if (enum != null) put("enum", buildJsonArray { enum.forEach { add(it) } })
    }

    private fun int(description: String, default: Int? = null, minimum: Int? = null, maximum: Int? = null) = buildJsonObject {
        put("type", "integer")
        put("description", description)
        if (default != null) put("default", default)
        if (minimum != null) put("minimum", minimum)
        if (maximum != null) put("maximum", maximum)
    }

    private fun num(description: String) = buildJsonObject {
        put("type", "number")
        put("description", description)
    }

    private fun bool(description: String, default: Boolean? = null) = buildJsonObject {
        put("type", "boolean")
        put("description", description)
        if (default != null) put("default", default)
    }

    private fun obj(description: String) = buildJsonObject {
        put("type", "object")
        put("description", description)
    }

    private fun arr(description: String, itemType: String = "string") = buildJsonObject {
        put("type", "array")
        put("description", description)
        put("items", buildJsonObject { put("type", itemType) })
    }

    private fun schema(required: List<String> = emptyList(), props: Map<String, JsonObject>) = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
        if (required.isNotEmpty()) {
            put("required", buildJsonArray { required.forEach { add(it) } })
        }
        put("additionalProperties", false)
    }

    private val locator = mapOf(
        "index" to int("Element index from read_screen. Preferred locator."),
        "bounds" to buildJsonObject {
            put("type", "array")
            put("description", "Four integers [left, top, right, bottom].")
            put("items", buildJsonObject { put("type", "integer") })
            put("minItems", 4)
            put("maxItems", 4)
        },
        "text" to str("Visible text or content description fallback selector."),
        "x" to num("Raw x coordinate. Last resort."),
        "y" to num("Raw y coordinate. Last resort."),
    )

    val ui: List<ToolDefinition> = listOf(
        ToolDefinition(
            "read_screen",
            "Read the current screen as a compact indexed accessibility tree with bounds, text, content descriptions and clickable state.",
            schema(props = mapOf(
                "changes_only" to bool("Return only nodes changed since the last read.", false),
                "include_bounds" to bool("Include pixel bounds for each node.", true),
                "max_nodes" to int("Node cap for the returned tree.", 150, 10, 400),
                "include_icons" to bool("Merge on-device icon detection when available.", false),
            )),
            ToolDomain.UI,
        ),
        ToolDefinition(
            "tap",
            "Tap a UI element by index, bounds, text, or coordinates. Exactly one locator strategy is used; index is preferred.",
            schema(props = locator + mapOf("long_press" to bool("Long-press instead of tap.", false))),
            ToolDomain.UI, mutating = true,
        ),
        ToolDefinition(
            "swipe",
            "Swipe across the screen between two points.",
            schema(
                required = listOf("from_x", "from_y", "to_x", "to_y"),
                props = mapOf(
                    "from_x" to num("Start x."), "from_y" to num("Start y."),
                    "to_x" to num("End x."), "to_y" to num("End y."),
                    "duration_ms" to int("Gesture duration.", 300, 50, 3000),
                ),
            ),
            ToolDomain.UI, mutating = true,
        ),
        ToolDefinition(
            "scroll",
            "Scroll the current screen in a direction.",
            schema(
                required = listOf("direction"),
                props = mapOf(
                    "direction" to str("Scroll direction.", "down", listOf("up", "down", "left", "right")),
                    "amount" to num("Fraction of the viewport to scroll, 0.1–0.9."),
                    "index" to int("Scrollable container index from read_screen."),
                ),
            ),
            ToolDomain.UI, mutating = true,
        ),
        ToolDefinition(
            "set_value",
            "Set the text of an editable field directly, without key events.",
            schema(
                required = listOf("text"),
                props = mapOf("index" to int("Field index from read_screen."), "text" to str("Text to set.")),
            ),
            ToolDomain.UI, mutating = true,
        ),
        ToolDefinition(
            "type_text",
            "Type text into the focused field or a chosen field using key injection.",
            schema(
                required = listOf("text"),
                props = mapOf(
                    "text" to str("Text to type."),
                    "index" to int("Field index from read_screen."),
                    "submit" to bool("Press enter after typing.", false),
                ),
            ),
            ToolDomain.UI, mutating = true,
        ),
        ToolDefinition(
            "press_key",
            "Press a navigation or editing key.",
            schema(
                required = listOf("key"),
                props = mapOf(
                    "key" to str("Key name.", enum = listOf("back", "home", "recents", "enter", "delete", "tab", "escape")),
                    "keycode" to int("Raw Android key code, used when key name is not enough."),
                ),
            ),
            ToolDomain.UI, mutating = true,
        ),
        ToolDefinition("press_enter", "Press the enter key on the current field.", schema(), ToolDomain.UI, mutating = true),
        ToolDefinition(
            "wait_for",
            "Wait until a text, element or app condition becomes true.",
            schema(
                required = listOf("condition"),
                props = mapOf(
                    "condition" to str("Text that must appear, or 'idle' for a settled screen."),
                    "timeout_ms" to int("Maximum wait.", 8000, 250, 60000),
                ),
            ),
            ToolDomain.UI,
        ),
        ToolDefinition(
            "verify_action",
            "Observe the screen and report whether the expectation holds. Never mutates.",
            schema(
                required = listOf("expectation"),
                props = mapOf(
                    "expectation" to str("What should be true, e.g. 'chat with Mom is open'."),
                    "index" to int("Element index that should now be visible."),
                ),
            ),
            ToolDomain.UI,
        ),
        ToolDefinition(
            "run_steps",
            "Execute a batch of tool calls in order. Each step is {tool, args}. Stops on first failure.",
            schema(
                required = listOf("steps"),
                props = mapOf(
                    "steps" to arr("Ordered tool calls.", "object"),
                    "verify_each" to bool("Observe after each mutating step.", true),
                ),
            ),
            ToolDomain.UI, mutating = true,
        ),
        ToolDefinition(
            "get_screenshot",
            "Capture the current screen to a PNG and return its path. One frame is held at a time.",
            schema(props = mapOf("max_dimension" to int("Downscale so the longest edge is at most this many pixels.", 1080, 320, 2160))),
            ToolDomain.UI,
        ),
        ToolDefinition(
            "perceive_screen",
            "Run optional on-device perception (icon detection and/or OCR) over the current frame.",
            schema(props = mapOf(
                "mode" to str("Perception mode.", "both", listOf("icons", "ocr", "both")),
                "max_dimension" to int("Downscale before inference.", 1080, 320, 2160),
            )),
            ToolDomain.PERCEPTION,
        ),
        ToolDefinition(
            "request_screen_capture_permission",
            "Ask the user to grant MediaProjection screen capture.",
            schema(),
            ToolDomain.UI,
        ),
    )

    val apps: List<ToolDefinition> = listOf(
        ToolDefinition(
            "launch_app",
            "Launch an installed app by name or package.",
            schema(props = mapOf("app_name" to str("Human app name."), "package" to str("Android package name."))),
            ToolDomain.APPS, mutating = true,
        ),
        ToolDefinition("lookup_app", "Resolve an app name to installed packages.", schema(required = listOf("query"), props = mapOf("query" to str("App name or partial name."))), ToolDomain.APPS),
        ToolDefinition("open_deeplink", "Open a deep link URI, optionally pinned to a package.", schema(required = listOf("uri"), props = mapOf("uri" to str("URI to open."), "package" to str("Target package."))), ToolDomain.APPS, mutating = true),
        ToolDefinition("resolve_deeplink", "Check which apps can handle a URI.", schema(required = listOf("uri"), props = mapOf("uri" to str("URI to resolve."))), ToolDomain.APPS),
        ToolDefinition("list_app_deeplinks", "List known deep links for an installed app.", schema(required = listOf("package"), props = mapOf("package" to str("Android package name."))), ToolDomain.APPS),
    )

    val data: List<ToolDefinition> = listOf(
        ToolDefinition("find_files", "Search the device's shared storage and media index.", schema(required = listOf("query"), props = mapOf("query" to str("Name or keyword."), "mime" to str("MIME filter."), "limit" to int("Maximum results.", 20, 1, 100))), ToolDomain.DATA),
        ToolDefinition("open_file", "Open a file or content URI with the default handler.", schema(props = mapOf("uri" to str("Content URI."), "path" to str("Absolute path."))), ToolDomain.DATA, mutating = true),
        ToolDefinition("clipboard", "Read or write the clipboard.", schema(required = listOf("action"), props = mapOf("action" to str("Operation.", enum = listOf("get", "set")), "text" to str("Text to write."))), ToolDomain.DATA),
        ToolDefinition("resolve_contact", "Resolve a spoken or typed contact name to phone numbers and emails.", schema(required = listOf("query"), props = mapOf("query" to str("Name or alias."), "limit" to int("Maximum matches.", 5, 1, 20))), ToolDomain.DATA),
        ToolDefinition("dial", "Open the dialer with a number, or place a call when direct_call is true.", schema(props = mapOf("number" to str("Phone number."), "contact" to str("Contact name to resolve."), "direct_call" to bool("Place the call instead of opening the dialer.", false))), ToolDomain.DATA, mutating = true, risky = true),
        ToolDefinition("compose_sms", "Open the SMS composer pre-filled for a number or contact.", schema(props = mapOf("to" to str("Number or contact name."), "body" to str("Message body."), "send" to bool("Send immediately instead of composing.", false))), ToolDomain.DATA, mutating = true, risky = true),
    )

    val system: List<ToolDefinition> = listOf(
        ToolDefinition("get_device_status", "Battery, network, device model, API level, next alarm, accessibility state and screen state.", schema(), ToolDomain.SYSTEM),
        ToolDefinition("volume", "Read or change a volume stream.", schema(props = mapOf("stream" to str("Audio stream.", "music", listOf("music", "ring", "alarm", "notification", "call")), "action" to str("Operation.", "get", listOf("get", "set", "up", "down", "mute", "unmute")), "level" to int("Target level 0–100."))), ToolDomain.SYSTEM, mutating = true),
        ToolDefinition("system_intent", "Open a system surface: settings page, alarm, timer, share sheet or a broadcast intent.", schema(required = listOf("action"), props = mapOf("action" to str("System action.", enum = listOf("wifi_settings", "bluetooth_settings", "battery_settings", "app_settings", "alarm", "timer", "share", "open_url", "brightness", "airplane_mode", "dnd")), "value" to str("Action value, e.g. URL, alarm time, seconds."), "package" to str("Package for app settings."))), ToolDomain.SYSTEM, mutating = true, risky = true),
        ToolDefinition("watch_device_events", "Observe accessibility and notification events for a bounded window.", schema(props = mapOf("events" to arr("Event types.", "string"), "duration_ms" to int("Observation window.", 5000, 500, 60000))), ToolDomain.SYSTEM),
    )

    val media: List<ToolDefinition> = listOf(
        ToolDefinition("media_control", "Control active media playback.", schema(required = listOf("action"), props = mapOf("action" to str("Playback action.", enum = listOf("play", "pause", "play_pause", "next", "previous", "stop")), "package" to str("Target media app."))), ToolDomain.MEDIA, mutating = true),
        ToolDefinition("get_media_sessions", "List active media sessions and their metadata.", schema(), ToolDomain.MEDIA),
        ToolDefinition("read_notifications", "Read current notifications.", schema(props = mapOf("limit" to int("Maximum notifications.", 20, 1, 100), "package" to str("Filter by package."))), ToolDomain.NOTIFICATIONS),
        ToolDefinition("notification_action", "Invoke an action on a notification.", schema(required = listOf("key", "action"), props = mapOf("key" to str("Notification key."), "action" to str("Action title or index."))), ToolDomain.NOTIFICATIONS, mutating = true, risky = true),
        ToolDefinition("dismiss_notification", "Dismiss a notification by key.", schema(required = listOf("key"), props = mapOf("key" to str("Notification key."))), ToolDomain.NOTIFICATIONS, mutating = true),
    )

    val web: List<ToolDefinition> = listOf(
        ToolDefinition("web_search", "Search the web with Tavily using the user's own API key.", schema(required = listOf("query"), props = mapOf("query" to str("Search query."), "max_results" to int("Number of results.", 5, 1, 10))), ToolDomain.WEB),
    )

    val browser: List<ToolDefinition> = listOf(
        ToolDefinition("browser_open", "Open a URL in the dedicated AURA browser window and return the page summary.", schema(required = listOf("url"), props = mapOf("url" to str("URL to load."))), ToolDomain.BROWSER, mutating = true),
        ToolDefinition("browser_read", "Read the current page as text.", schema(props = mapOf("selector" to str("CSS selector to read."), "max_chars" to int("Text cap.", 12000, 200, 60000))), ToolDomain.BROWSER),
        ToolDefinition("browser_find", "Find text in the current page and return context and element handles.", schema(required = listOf("query"), props = mapOf("query" to str("Text to find."))), ToolDomain.BROWSER),
        ToolDefinition("browser_act", "Click, type, scroll or press keys in the current page.", schema(required = listOf("action"), props = mapOf("action" to str("Interaction.", enum = listOf("click", "type", "scroll", "press", "select")), "selector" to str("CSS selector or handle."), "text" to str("Text to type."), "direction" to str("Scroll direction.", "down", listOf("up", "down", "left", "right")))), ToolDomain.BROWSER, mutating = true),
        ToolDefinition("browser_extract", "Extract structured data from the page.", schema(props = mapOf("schema" to obj("Field map as JSON."), "selector" to str("Container selector."))), ToolDomain.BROWSER),
        ToolDefinition("browser_tabs", "List, open, switch or close browser tabs.", schema(required = listOf("action"), props = mapOf("action" to str("Tab operation.", enum = listOf("list", "open", "switch", "close")), "index" to int("Tab index."), "url" to str("URL for a new tab."))), ToolDomain.BROWSER),
        ToolDefinition("browser_wait", "Wait for a page condition.", schema(required = listOf("condition"), props = mapOf("condition" to str("Selector, text or 'idle'."), "timeout_ms" to int("Maximum wait.", 10000, 250, 60000))), ToolDomain.BROWSER),
        ToolDefinition("browser_screenshot", "Screenshot the browser viewport.", schema(), ToolDomain.BROWSER),
        ToolDefinition("browser_upload", "Attach a local file to a file input.", schema(required = listOf("selector", "path"), props = mapOf("selector" to str("File input selector."), "path" to str("Absolute path or content URI."))), ToolDomain.BROWSER, mutating = true, risky = true),
        ToolDefinition("browser_handoff", "Hand the current URL to the user's real browser and pause automation.", schema(props = mapOf("reason" to str("Why the user must take over."))), ToolDomain.BROWSER, mutating = true),
        ToolDefinition("browser_close", "Close the AURA browser and return control to the previous app.", schema(), ToolDomain.BROWSER, mutating = true),
    )

    val session: List<ToolDefinition> = listOf(
        ToolDefinition("end_session", "End the current agent run with an outcome and summary.", schema(props = mapOf("outcome" to str("Outcome.", "success", listOf("success", "failure", "cancelled", "budget_exhausted", "permission_denied")), "summary" to str("One-line summary for the user."))), ToolDomain.SESSION),
    )

    val all: List<ToolDefinition> =
        ui + apps + data + system + media + web + browser + session

    val byName: Map<String, ToolDefinition> = all.associateBy { it.name }

    fun require(name: String): ToolDefinition =
        byName[name] ?: error("Unknown tool: $name")

    fun namesByDomain(): Map<ToolDomain, List<String>> =
        all.groupBy { it.domain }.mapValues { (_, list) -> list.map { it.name } }

    fun toolListJson(names: Set<String>? = null): List<JsonObject> =
        all.filter { names == null || it.name in names }.map { it.toJson() }

    /** Used by the eval harness and tool inventory screen. */
    fun describe(name: String): String = (byName[name]?.description ?: "Unknown tool")

    /** Compact signature string, e.g. "tap(index: int, ...)". */
    fun signature(name: String): String {
        val def = byName[name] ?: return name
        val props = def.inputSchema["properties"] as? JsonObject ?: return "$name()"
        val required = (def.inputSchema["required"]?.let { it as? kotlinx.serialization.json.JsonArray })
            ?.mapNotNull { (it as? JsonPrimitive)?.content }?.toSet() ?: emptySet()
        val parts = props.keys.map { key ->
            if (key in required) "$key" else "$key?"
        }
        return "$name(${parts.joinToString(", ")})"
    }
}
