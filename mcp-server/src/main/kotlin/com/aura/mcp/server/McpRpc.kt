package com.aura.mcp.server

import com.aura.mcp.bridge.ToolDefinition
import com.aura.mcp.tools.ToolCatalog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Shared JSON-RPC building blocks for the LAN (Ktor HTTPS) and in-process MCP transports.
 * Kept transport-agnostic so the two cannot drift on the wire.
 */
object McpRpc {

    const val PROTOCOL_VERSION = "2025-03-26"
    const val SERVER_NAME = "aura"
    const val SERVER_VERSION = com.aura.mcp.BuildConfig.VERSION_NAME

    /** Connect-time instructions clients receive from `initialize`. */
    const val INSTRUCTIONS =
        "AURA is an on-device agent for Android. It reads the screen with an accessibility service,\n" +
            "taps, swipes, types and fills forms, drives a real in-app browser, and can launch apps or open\n" +
            "install listings with deep links when possible. Prefer perceive-first: never guess a locator without reading the\n" +
            "screen first. Prefer deep links over multi-step UI flows. Verify after every mutating step:\n" +
            "re-read the screen before acting again. Refuse to act when the goal is ambiguous — ask_user.\n" +
            "Safety policy:\n" +
            "  1. Refuse to open banking, payment, wallet, brokerage or authenticator apps; the bridge\n" +
            "     also hard-blocks them.\n" +
            "  2. Never type card numbers, CVVs, PINs, passwords or one-time codes into any field.\n" +
            "  3. Never silence or bypass restrictions on security settings without explicit user intent.\n" +
            "  4. Never permanently delete data, uninstall apps, or factory-reset on inference alone.\n" +
            "  5. Destructive goals require explicit user confirmation before execution.\n" +
            "  6. Never take irreversible actions beyond the user's stated goal.\n" +
            "The full policy is available at resource `aura://safety/policy`; the tool selection guide is at\n" +
            "`aura://tools/guide`; live device info is at `aura://device/status`."

    val SAFETY_POLICY: String =
        "AURA safety policy\n" +
            "=================\n" +
            "Device boundaries\n" +
            "- These app classes are never opened, regardless of goal: banking, payments, wallets,\n" +
            "  brokerages, tax, and authenticator apps (2FA). The bridge enforces this independently of the LLM.\n" +
            "- No security-setting changes that weaken the device (locks, biometrics off, unknown sources on).\n" +
            "- No factory reset, no uninstall of user/apps/system components, no data wipe without explicit\n" +
            "  user confirmation.\n" +
            "- No typing of card numbers, CVVs, PINs, passwords or one-time codes.\n" +
            "Confirmation\n" +
            "- Goals matching destructive patterns (uninstall, wipe, reset, purchase, transfer, send to unknown\n" +
            "  recipient, mass message) require explicit approval via the in-app gate before execution.\n" +
            "- ask_user is the sanctioned way to resolve ambiguity; the agent must use it when the goal is\n" +
            "  underdetermined, never guess through it.\n" +
            "Observation\n" +
            "- Every observation uses data the user can see on their own screen; when the agent is unsure what\n" +
            "  changed, it re-reads the screen instead of assuming.\n" +
            "Privacy\n" +
            "- Screen content goes only to the user's own configured LLM endpoint; nothing is sent anywhere\n" +
            "  else unless the user explicitly enables cloud sync (Firebase) or B2 backup (Backblaze).\n" +
            "- All provider keys, MCP tokens and device secrets live in EncryptedSharedPreferences on-device.\n" +
            "- The MCP server is local-network only; there is no remote relay by design.\n" +
            "Logging\n" +
            "- Every tool call is logged on-device in the session trace and pruned by the retention worker.\n" +
            "Audit logs may be exported as a session bundle; they are only uploaded if the user asks (or\n" +
            "  enables session sync in Firebase settings).\n" +
            "Consent\n" +
            "- AURA runs only while the user sees it; background wake word (Hey aura) requires an explicit\n" +
            "  opt-in in Voice settings and can be revoked there."

    const val TOOL_GUIDE: String =
        "AURA tool selection guide\n" +
            "========================\n" +
            "1. Start with perceive_screen, never with a gesture. Your first action on any unfamiliar screen is\n" +
            "   `perceive_screen` or `read_screen`; act only on elements you have observed.\n" +
            "2. Prefer deep links. `resolve_deeplink`/`open_deeplink` and `system_intent` take you directly to a\n" +
            "   target screen; multi-step UI flows are slower and more brittle.\n" +
            "3. Prefer reading over tapping. `read_notifications` and `get_media_sessions` settle many goals in\n" +
            "   one call when the answer is on screen.\n" +
            "4. Call `wait_for` after animations; `verify_action` before claiming done.\n" +
            "5. Need several independent taps? `run_steps` batches them in one round-trip.\n" +
            "6. Prefer `set_value` over type_text for text fields (atomic, no focus needed).\n" +
            "7. Every mutating tool returns `post_action_observation`: use it instead of `wait_for` when present.\n" +
            "8. Use `ask_user` when the goal is ambiguous or the screen makes no sense; never guess.\n" +
            "9. Browser tasks (research, forms) belong in `browser_*`, which understands pages; apps may block\n" +
            "   webviews, so open links in the AURA browser instead of the app's built-in one.\n" +
            "10. End every session with `end_session(outcome=…, summary=…)` — the report the user reads.\n" +
            "11. To install an app that is not installed yet, call `install_app` with the app name or package; it opens the Google Play Store listing where the install step happens.\n" +
            "    Then read the screen and tap the Install action when it is visible.\n" +
            "12. To search inside a music or video app (e.g. YouTube for a song), prefer `browser_open` with the\n" +
            "    app's search URL (e.g. https://www.youtube.com/results?search_query=<term>); if the app UI must be\n" +
            "    driven instead, launch the app, read_screen, tap the search affordance, type_text the query and submit."

    // ──────────────────────────────────────────────────────────────────────
    // RPC envelopes (shared by both transports)
    // ──────────────────────────────────────────────────────────────────────

    fun initializeResult(): JsonObject = buildJsonObject {
        put("protocolVersion", PROTOCOL_VERSION)
        putJsonObject("serverInfo") {
            put("name", SERVER_NAME)
            put("version", SERVER_VERSION)
        }
        putJsonObject("capabilities") {
            putJsonObject("tools") {}
            putJsonObject("prompts") {}
            putJsonObject("resources") {}
        }
        put("instructions", INSTRUCTIONS)
    }

    fun error(id: JsonElement?, code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: kotlinx.serialization.json.JsonNull)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }

    fun result(id: JsonElement?, payload: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: kotlinx.serialization.json.JsonNull)
        put("result", payload)
    }

    fun buildSuccess(id: JsonElement?, payload: JsonObject): JsonObject = result(id, payload)

    fun toolsListJson(tools: List<ToolDefinition> = ToolCatalog.all): JsonArray =
        buildJsonArray { tools.forEach { add(it.toJson()) } }

    // ──────────────────────────────────────────────────────────────────────
    // Resources (static policy docs + a live device snapshot)
    // ──────────────────────────────────────────────────────────────────────

    private val DEVICE_MODEL: String? = runCatching { android.os.Build.MODEL }.getOrNull()
    private val DEVICE_MANUFACTURER: String? = runCatching { android.os.Build.MANUFACTURER }.getOrNull()
    private val DEVICE_SDK: Int = runCatching { android.os.Build.VERSION.SDK_INT }.getOrDefault(0)
    private val DEVICE_RELEASE: String? = runCatching { android.os.Build.VERSION.RELEASE }.getOrNull()

    fun deviceStatusJson(): JsonObject = buildJsonObject {
        put("model", DEVICE_MODEL ?: "unknown")
        put("manufacturer", DEVICE_MANUFACTURER ?: "unknown")
        put("sdk_int", DEVICE_SDK)
        put("android_release", DEVICE_RELEASE ?: "unknown")
        put("server_version", SERVER_VERSION)
        put("tool_count", ToolCatalog.all.size)
        put("mcp_transport", "local-network only; no remote relay")
        put("cloud_opt_in", "Firebase sync disabled unless the user enables it in Settings")
    }

    fun resourcesList(): JsonArray = buildJsonArray {
        add(resourceDescriptor("aura://safety/policy", "AURA safety policy", "The non-negotiable limits every agent and MCP client must respect. Read this before your first call."))
        add(resourceDescriptor("aura://tools/guide", "AURA tool selection guide", "How to select tools: perceive first, prefer deep links, verify after acting."))
        add(resourceDescriptor("aura://device/status", "Device status", "Live snapshot: model, Android version, SDK level, server version and tool count."))
    }

    private fun resourceDescriptor(uri: String, name: String, description: String): JsonObject =
        buildJsonObject {
            put("uri", uri)
            put("name", name)
            put("description", description)
            put("mimeType", "text/plain")
        }

    fun readResource(uri: String): JsonObject? = when (uri) {
        "aura://safety/policy" -> buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("uri", uri)
                    put("mimeType", "text/plain")
                    put("text", SAFETY_POLICY)
                })
            })
        }

        "aura://tools/guide" -> buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("uri", uri)
                    put("mimeType", "text/plain")
                    put("text", TOOL_GUIDE)
                })
            })
        }

        "aura://device/status" -> buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("uri", uri)
                    put("mimeType", "application/json")
                    put("text", deviceStatusJson().toString())
                })
            })
        }

        else -> null
    }

    // ──────────────────────────────────────────────────────────────────────
    // Prompts (task templates; every built-in skill is also an invocable prompt)
    // ──────────────────────────────────────────────────────────────────────

    fun promptsList(): JsonArray = buildJsonArray {
        add(promptDescriptor("research_then_act", "Research, then act", "If the flow is unfamiliar, web_search the exact app-and-action first, then act step by step.",
            listOf(
                promptArg("app", "The app you need to operate (e.g. WhatsApp).", required = true),
                promptArg("goal", "One sentence describing the target end state.", required = true),
            )))
        add(promptDescriptor("whatsapp_message", "WhatsApp a contact", "Resolve the contact, prefer the wa.me deep link, verify the chat before sending.",
            listOf(
                promptArg("contact", "Name of the person or group.", required = true),
                promptArg("message", "Message text to send.", required = true),
            )))
        add(promptDescriptor("compare_products", "Compare two products", "Open the listing in two browser tabs, extract price, rating and stock, and report — without buying.",
            listOf(
                promptArg("item", "Product to compare.", required = true),
                promptArg("stores", "Comma-separated stores to check (default: Amazon, Flipkart, Google Shopping).", required = false),
            )))
        add(promptDescriptor("book_ride", "Book a ride", "Call the local ride-hailing service via its app or deep link and book; confirm before confirming payment.",
            listOf(
                promptArg("pickup", "Pickup location.", required = true),
                promptArg("dropoff", "Drop-off location.", required = true),
                promptArg("service", "Preferred service (default: Rapido / Uber / Ola, as on the phone).", required = false),
            )))
        add(promptDescriptor("summarize_page", "Summarize a web page", "Open the URL in the AURA browser, read it, summarize the useful bits (no actions).",
            listOf(
                promptArg("url", "Page to summarize.", required = true),
                promptArg("length", "One-line or detailed.", required = false),
            )))
    }

    private fun promptDescriptor(
        name: String,
        description: String,
        goal: String,
        arguments: List<JsonObject>,
    ): JsonObject = buildJsonObject {
        put("name", name)
        put("description", description)
        put("arguments", buildJsonArray { arguments.forEach { add(it) } })
    }

    private fun promptArg(name: String, description: String, required: Boolean): JsonObject =
        buildJsonObject {
            put("name", name)
            put("description", description)
            put("required", required)
        }

    fun getPrompt(name: String, arguments: JsonObject): JsonObject? {
        val args = arguments.toString()
        fun asMap(): Map<String, String> = arguments.entries
            .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }
            .toMap()
        val map = runCatching { asMap() }.getOrElse { emptyMap() }
        val text = when (name) {
            "research_then_act" ->
                "Goal: do \"${map["goal"] ?: "(goal)"}\" in the app ${map["app"] ?: "(app)"} on this phone.\n" +
                    "1. web_search the exact task in that app on Android; read the top result.\n" +
                    "2. perceive_screen; prefer deep links or system_intent over multi-step UI.\n" +
                    "3. Execute step by step; verify after each mutating step; re-read instead of assuming.\n" +
                    "4. If anything is unclear, call ask_user instead of guessing.\n" +
                    "5. Call end_session with outcome and one-line summary."

            "whatsapp_message" ->
                "Goal: send \"${map["message"] ?: "(message)"}\" to ${map["contact"] ?: "(contact)"} on WhatsApp.\n" +
                    "1. resolve_contact first. If ambiguous, ask_user; do not guess.\n" +
                    "2. Prefer open_deeplink with wa.me/<number>?text=<urlencoded message>.\n" +
                    "3. If the deep link fails, drive WhatsApp manually: search, open chat, type, verify the chat title matches the intended recipient, then send.\n" +
                    "4. Verify the sent bubble appears before reporting success."

            "compare_products" ->
                "Goal: compare \"${map["item"] ?: "(item)"}\" across the stores (default: ${map["stores"] ?: "Amazon, Flipkart and Google Shopping"}).\n" +
                    "1. browser_open each store's listing search.\n" +
                    "2. browser_extract price, rating and stock status from each.\n" +
                    "3. Return a table of the three numbers and which is the better deal. Do not buy, add to cart or sign in."

            "book_ride" ->
                "Goal: book a ride from ${map["pickup"] ?: "(pickup)"} to ${map["dropoff"] ?: "(dropoff)"}${map["service"]?.let { " via $it" } ?: ""}.\n" +
                    "1. open the ride-hailing app (or a deep link if known).\n" +
                    "2. set pickup and destination with set_value; confirm the pickup point on the map card.\n" +
                    "3. show the quoted fare to the user BEFORE confirming the ride; never tap Pay/Confirm without an explicit yes."

            "summarize_page" ->
                "Goal: summarize ${map["url"] ?: "(url)"}${map["length"]?.let { " ($it)" } ?: ""}.\n" +
                    "1. browser_open the URL; browser_read to capture the text.\n" +
                    "2. Summarize the useful parts in bullet points; do not click anything else; do not sign in.\n" +
                    "3. Return the summary as text."

            else -> return null
        }
        return buildJsonObject {
            put("description", "Task template: $name (with arguments $args)")
            put("prompt", buildJsonObject {
                put("name", name)
                put("description", name)
                put("arguments", buildJsonArray { })
            })
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", buildJsonObject {
                        put("type", "text")
                        put("text", text)
                    })
                })
            })
        }
    }
}
