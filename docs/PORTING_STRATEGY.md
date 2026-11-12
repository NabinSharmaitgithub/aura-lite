# Porting Strategy

Guidance for reusing AURA Lite subsystems in another Android project (or a fork/branch).

## Boundaries to preserve
- **`:mcp-server` stays Android-free** (no accessibility/Compose). Keep `DeviceBridge`, `ToolCatalog`, registry, guards, caches, and server core portable.
- **App supplies the bridge** (`AuraDeviceBridge`) for accessibility, apps, browser, notifications, media, capture, data, voice.
- **One catalog + one pipeline** for both in-app agent and remote clients.

## Port order
1. **Core contracts:** `ToolDefinition`, `ToolResult`/`ToolError`, `DeviceBridge`, `ToolCatalog`, `McpToolRegistry`, `ToolPipelineHooks`, `ToolPrincipal`.
2. **Policy/guards:** `SensitivePolicy`, `ActionGuard`, `ForegroundGuard`, `OtpSighting`, restricted apps/approval gate.
3. **Caches/perception contracts:** `PerceptionCache`, `CvMemo`, `LastSeenScreen`; map to your UI-tree/screenshot implementation.
4. **Server/transports:** `InProcessMcpServer`/`McpClient`, then HTTP/WebRTC if needed; keep refusal envelopes identical.
5. **Agent runtime:** prompt builder, ledger/budget, strategy loop, completion evidence, checkpointing.
6. **Voice/assistant:** wake word/STT/TTS entry points (optional; platform-dependent).
7. **Optional integrations:** Firebase/B2/research/remote gate.

## Adaptation checklist
- Replace `PackageManager`/accessibility specifics in your bridge; keep tool schemas stable.
- Implement perception generation/signature semantics for your UI framework.
- Provide secure key storage and principal/trust storage appropriate to your app.
- Recreate permission metadata registry and feature gating for your UX.
- Re-validate OEM-specific behavior (background services, microphone, accessibility, notifications).

## Avoid
- Bypassing the registry pipeline for “quick” device actions.
- Prompt-only safety enforcement.
- Sharing mutable global state across runs without principal/run scoping.
- Coupling `:mcp-server` to Android framework classes.
