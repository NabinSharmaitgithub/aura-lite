# Backend Schema — AURA for Android

> AURA has **no server we operate**. "Backend" = the on-device persistence layer + the MCP
> server running on the phone. This document defines every schema you must reproduce.

---

## 1. Storage topology

```
┌─ Room (SQLite) ────────────── aura.db ── schema v1
├─ SharedPreferences ────────── aura_settings.xml
├─ EncryptedSharedPreferences ─ provider keys, Tavily key
├─ DataStore (preferences) ──── ui/feature flags
├─ filesDir/sessions/<id>/ ──── session.jsonl, step screenshots
├─ filesDir/learnings/ ──────── learnings.json, aliases
├─ filesDir/skills/ ─────────── user skill packs
└─ cacheDir/ ─────────────────── trimmed frames, model scratch
```

**Rule:** nothing here is ever uploaded. Only the LLM request body (constructed from the current goal + tool results) leaves the device.

---

## 2. Room schema (`AuraDatabase`, version 1)

### 2.1 `aura_contacts`
| Column | Type | Notes |
|---|---|---|
| `contactId` | TEXT PK | Android contact id |
| `displayName` | TEXT | as shown by Contacts |
| `phoneNumbers` | TEXT | JSON array of strings |
| `emails` | TEXT | JSON array of strings |
| `photoUri` | TEXT? | nullable |
| `phoneticKey` | TEXT | Double Metaphone of first token, e.g. `Elakiya → ALK` |
| `phoneticKeyAlt` | TEXT | alternate code |
| `aliases` | TEXT | JSON array of confirmed STT spellings |
| `lastSyncedAt` | INTEGER | epoch ms |

Indices: `phoneticKey`, `phoneticKeyAlt`.

Purpose: contact resolution for `dial` / `compose_sms` / `resolve_contact` in 4 stages —
1. exact alias/name, 2. normalised, 3. phonetic (DB-hit skips recompute), 4. fuzzy; after 5 confirmed corrections the STT spelling is appended to `aliases`.

### 2.2 `stt_corrections`
| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK AUTOINCREMENT | |
| `heard` | TEXT | raw STT output |
| `corrected` | TEXT | user's fix |
| `count` | INTEGER | confirmed occurrences |
| `lastSeenAt` | INTEGER | epoch ms |

Purpose: drives the STT learning loop; `count ≥ 5` promotes an alias.

### 2.3 DAOs
`AudioDao`, `AuraContactDao`, `SttCorrectionDao`. `@TypeConverters(Converters::class)`, `exportSchema = false`.

**Migration policy:** bump `version`, add a `Migration`, never `fallbackToDestructiveMigration()` in a build you intend to ship to yourself (you lose learnings).

### 2.4 Session/trace persistence
Session metadata + steps are persisted as **JSONL files**, not Room, so a process kill leaves a readable trace and the DB stays small.

`filesDir/sessions/<sessionId>/session.jsonl` — one JSON object per line:

```json
{"t":1731234567890,"type":"session_start","session":"s_01H...","goal":"send mom the photo","provider":"groq","model":"meta-llama/llama-4-scout-17b-16e-instruct"}
{"t":1731234568123,"type":"thought","text":"I need to open Gallery first."}
{"t":1731234568400,"type":"tool_call","id":"c1","name":"launch_app","args":{"app_name":"Photos"}}
{"t":1731234569001,"type":"tool_result","id":"c1","ok":true,"changed":true,"duration_ms":612,"observed":{"package":"com.google.android.apps.photos"}}
{"t":1731234570100,"type":"screenshot","path":"step_03.png","generation":42}
{"t":1731234572000,"type":"answer","text":"Sent the photo to Mom."}
{"t":1731234572100,"type":"session_end","outcome":"success","steps":6,"duration_ms":6411}
```

Enumerated `type`: `session_start`, `thought`, `plan`, `tool_call`, `tool_result`, `verify`, `screenshot`, `ask_user`, `user_answer`, `error`, `interrupt`, `session_end`.
`outcome`: `success`, `failure`, `cancelled`, `budget_exhausted`, `permission_denied`.

Retention: `McpLogRetentionScreen` sets days (default 7); a WorkManager job prunes directories + Room rows.

---

## 3. SharedPreferences — `aura_settings`

| Key | Type | Default | Meaning |
|---|---|---|---|
| `onboarding_complete` | bool | false | gate for wizard |
| `onboarding_step` | int | 0 | resume index |
| `user_name` | string | "" | profile name |
| `wake_word_enabled` | bool | **false** | KWS service may start |
| `companion_enabled` | bool | false | voice companion mode |
| `screen_capture_enabled` | bool | false | MediaProjection consent cache |
| `read_screen_image_enabled` | bool | false | attach screenshots to LLM requests |
| `recordable_voice_enabled` | bool | false | custom recorded voice |
| `ui_stream_enabled` | bool | false | UI streaming overlay |
| `edge_glow_enabled` | bool | false | screen-edge activity glow |
| `learnings_enabled` | bool | true | extract durable preferences |
| `diagnostics_enabled` | bool | false | local diagnostics only |
| `developer_mode_enabled` | bool | false | exposes eval + retention |
| `legacy_python_backend_enabled` | bool | false | legacy WS path (off; no server) |
| `log_retention_days` | int | 7 | pruning |
| `llm_endpoint_id` | string | "groq" | selected provider |
| `llm_model_id` | string | "" | selected model |
| `temperature` | float | 0.2 | agent temperature |

> **Removed keys (do not resurrect):** `kill_switch_enabled`, `kill_switch_message`, `min_supported_version_code`, `version_gate_message`, `latest_version_code`, `update_url`, any `firebase_*`, `auth_*`, `sync_*`.

---

## 4. Secure storage (EncryptedSharedPreferences)

| Key name | Value |
|---|---|
| `provider_key_<endpointId>` | API key, e.g. `provider_key_nvidia` = `nvapi-…` |
| `provider_key_custom_<uuid>` | key for a custom endpoint |
| `tavily_api_key` | web search key |
| `mcp_pairing_secret` | server pairing material (see §6) |

Rules: AES256-GCM with MasterKey; created lazily; never migrate to plaintext; never log; never include in traces.

---

## 5. Learnings & skills

`filesDir/learnings/learnings.json`
```json
{
  "version": 1,
  "updatedAt": 1731234567890,
  "facts": [
    { "id": "l_001", "scope": "global", "text": "Prefers WhatsApp over SMS", "confidence": 0.9, "sources": ["s_01H..."] },
    { "id": "l_002", "scope": "com.whatsapp", "text": "Mom = first pinned chat", "confidence": 0.7 }
  ],
  "corrections": [ { "heard": "beat rice", "corrected": "Beatrice", "count": 6 } ]
}
```
- `scope` is `global` or a package name; app-scoped facts are only injected for that app.
- Confidence < 0.5 facts are not injected into prompts.
- User can delete any fact from Memory screen (hard delete + tombstone id to prevent re-learn).

`filesDir/skills/<skillId>/skill.json`
```json
{ "id": "send_photo", "name": "Send a photo", "version": 1,
  "trigger": "user asks to share a picture",
  "steps": ["open Photos", "long-press target", "tap Share", "choose app", "pick contact", "send"],
  "tools": ["launch_app","read_screen","tap","type_text","verify_action"],
  "guardrails": ["confirm before send"] }
```

---

## 6. MCP server schema

### 6.1 Transports
| Transport | Use |
|---|---|
| **In-process** (`InProcessMcpServer`) | the app's own agent — no sockets, no TLS, no auth |
| **LAN HTTPS** (Netty/Ktor) | external clients on the same network |
| **mDNS** | discovery (`_mcp._tcp` style advertisement) |

### 6.2 Pairing
1. Server generates a self-signed cert (BouncyCastle) + ephemeral key pair.
2. UI shows QR = `{ host, port, fingerprint, nonce }`.
3. Client posts nonce to `/pair`; server verifies fingerprint and returns a bearer token.
4. Token is stored per client (`trusted_devices`), revocable.

### 6.3 Trusted devices
| Field | Type |
|---|---|
| `clientId` | uuid |
| `name` | string (editable) |
| `tokenHash` | SHA-256 of token (never the token) |
| `pairedAt` / `lastSeenAt` | epoch ms |
| `scopes` | array of tool domains |
| `canApproveAuto` | bool (skip notification for low-risk tools) |

### 6.4 Protocol
JSON-RPC 2.0 / MCP: `initialize`, `tools/list`, `tools/call`, `prompts/list`, `prompts/get`, `ping`.
Header: `Authorization: Bearer <token>`.
Health: `GET /health` → `{ status, version, tools, clients, uptime }` (no auth, minimal info).

### 6.5 Tool definition shape
```json
{
  "name": "tap",
  "description": "Tap a UI element by index, bounds, or coordinates.",
  "inputSchema": {
    "type": "object",
    "properties": {
      "index":      { "type": "integer", "description": "element index from read_screen" },
      "bounds":     { "type": "array", "items": { "type": "integer" }, "minItems": 4, "maxItems": 4 },
      "x":          { "type": "number" },
      "y":          { "type": "number" },
      "text":       { "type": "string", "description": "fallback selector" },
      "long_press": { "type": "boolean", "default": false }
    },
    "required": []
  }
}
```
Constraints: exactly one locator strategy; index preferred; coordinates last resort.

### 6.6 Uniform result envelope
```json
{
  "ok": true,
  "changed": true,
  "observed": { "screen_generation": 42, "foreground": "com.whatsapp", "summary": "Chat list" },
  "error": null,
  "meta": { "duration_ms": 438, "cached": false, "screenshot": "step_03.png" }
}
```
Error form: `{"ok":false,"error":{"code":"permission_denied","message":"…","fix":"settings/accessibility"}}`
Codes: `permission_denied`, `not_found`, `ambiguous`, `blocked_app`, `timeout`, `stale_screen`, `unsupported`, `internal`.

### 6.7 Audit log (`McpAuditLogger`)
One line per call, append-only:
```json
{"t":1731234569001,"client":"cli_7f3","tool":"tap","args_hash":"9c1f…","ok":true,"duration_ms":412,"approved_by":"user","blocked":false}
```
Store arguments **hashed** in the audit log; full args live only in the session log the user can delete.

### 6.8 Screen cache
Key: `ScreenGeneration.current` (monotonic counter bumped on any UI change). Entries invalidate on new generation. Prevents the LLM re-reading an unchanged tree and prevents acting on stale coordinates.

---

## 7. LLM provider schema (client-side)

```kotlin
data class LlmEndpoint(
  val id: String,
  val displayName: String,
  val baseUrl: String,
  val chatCompletionsPath: String,
  val modelsUrl: String,
  val authScheme: AuthScheme,          // BEARER | X_GOOG_API_KEY | ANTHROPIC_X_API_KEY | NONE
  val modelListStyle: ModelListStyle,  // OPENAI_DATA | GEMINI_MODELS
  val visionInference: VisionInference,
  val reasoningInference: ReasoningInference,
  val profile: ProviderProfile,
  val isBuiltIn: Boolean,
  val keyHint: String,
  val keyUrl: String?,
  val defaultModelId: String?          // null = must be chosen from catalogue
)
```

Request contract: OpenAI chat-completions shape for every provider (Gemini and Anthropic are reached through their OpenAI-compatible surfaces). `tools` is the MCP tool list; `tool_choice: "auto"`.
Capability detection: derive vision/reasoning from the fetched model id and provider profile, **never** from a hard-coded assumption.

### Built-in registry
| id | base URL | key hint |
|---|---|---|
| `groq` | `https://api.groq.com` | `gsk_…` |
| `openrouter` | `https://openrouter.ai` | `sk-or-…` |
| `gemini` | `https://generativelanguage.googleapis.com` | `AIza…` |
| `openai` | `https://api.openai.com` | `sk-…` |
| `anthropic` | `https://api.anthropic.com` | `sk-ant-…` |
| `mistral` | `https://api.mistral.ai` | — |
| `dashscope` | `https://dashscope-intl.aliyuncs.com` | `sk-…` |
| `cerebras` | `https://api.cerebras.ai` | `csk-…` |
| `zai` | `https://api.z.ai` | — |
| `xai` | `https://api.x.ai` | `xai-…` |
| `together` | `https://api.together.xyz` | — |
| `moonshot` | `https://api.moonshot.ai` | `sk-…` |
| `deepseek` | `https://api.deepseek.com` | `sk-…` |
| `nvidia` | `https://integrate.api.nvidia.com` | `nvapi-…` |

---

## 8. Filesystem layout

```
filesDir/
├─ sessions/<sessionId>/{session.jsonl, step_01.png, …}
├─ learnings/learnings.json
├─ skills/<skillId>/skill.json
├─ screen/current.png            (single frame, replaced in place)
└─ models/                       (only if downloaded post-install)
cacheDir/
├─ frames/                       (evicted aggressively)
└─ http/                         (OkHttp cache)
```

Assets shipped in the APK (28 MB):
| Asset | Size | Purpose |
|---|---|---|
| `omniparser_icon_detect.onnx` | 20.6 MB | on-device icon detection |
| `silero_vad.onnx` | 2.3 MB | voice activity detection |
| `kws/` | [verify] | sherpa-onnx wake-word model |
| `skills/` | — | built-in skill packs |
| `eval/` | — | evaluation tasks |
| `judge/` | — | LLM-as-judge prompts |
| `onboarding/` | — | onboarding copy/media |
| `deeplink_catalog.json` | 6.7 KB | known app deep links |

> **Memory note:** the 20.6 MB ONNX model must be loaded **on demand** and released when a run ends. Eagerly creating an `OrtSession` at startup is the single biggest avoidable RSS cost.

---

## 9. Cloud schema (REMOVED — reference only)

The previous build used Firebase: Auth (Google/email/phone), Firestore (devices, gates, telemetry), Remote Config (kill switch/version gate), Crashlytics, Analytics. All of it is **out of scope**. If you ever add sync back, mirror these collections:

```
firestore/
├─ users/{uid}
├─ devices/{deviceId}          # last seen, version, gate state
├─ gates/current               # killSwitch{enabled,message}, versionGate{min,max,url}
├─ telemetry/{autoId}          # tool outcomes (hashed args)
└─ blocklists/{listId}         # package names
```

Do **not** reintroduce this unless the product direction changes; it was the source of the account/consent surface and the leaked-key risk.

---

## 10. Data lifecycle & privacy

| Data | Retention | User control |
|---|---|---|
| Sessions/traces | 7 days default | per-item delete, retention screen |
| Screenshots | 7 days | auto-pruned, never uploaded unless attached to a request |
| Learnings | until deleted | Memory screen |
| Provider keys | until removed | Brain screen |
| Contacts cache | re-synced | delete on logout-equivalent (none) |
| MCP audit | 7 days | retention screen |

No identifiers, no advertising ID, no device fingerprint. `QUERY_ALL_PACKAGES` is used only for local app resolution.
