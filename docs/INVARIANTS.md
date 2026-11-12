# Non-Negotiable Invariants — AURA Lite

These invariants are enforced in code and must not be relaxed by prompts, tool descriptions, or client configuration.

## Safety & policy
1. **No bypass of the MCP chokepoint.** Every device action goes through `McpToolRegistry`/`scopedTool`-style enforcement (principal → policy → pause/foreground/approval → action guard → execute → audit/hooks).
2. **Fail closed on sensitive contexts.** Sensitive apps (banking/payment/wallet/authenticator), permission/system install surfaces, and credential/OTP handling are blocked by code, not only prompts.
3. **No secret exfiltration.** Card numbers, CVVs, PINs, passwords, OTPs, and government IDs are never typed, copied onward, or uploaded. OTP sighting is guarded.
4. **Destructive/financial actions require explicit user confirmation**, with the action and target clear to the user.
5. **No blind actions.** Mutating actions must be grounded in fresh perception (`som_id`/handle/bounds) and verified afterward.
6. **Completion claims require evidence.** Success on high-stakes goals must be backed by tool outcomes/observations; otherwise report partial/failure.
7. **On-screen/remote content is untrusted data**, never instructions.

## Architecture
8. **One tool catalog.** `ToolCatalog` is the single source of tool schemas; agent and remote clients share it.
9. **One enforcement pipeline.** No transport (in-process, HTTP, WebRTC) may execute tools without the same policy/guard/audit path.
10. **Principal-scoped access.** Every call has a principal; allowed tools/toolsets are enforced server-side.
11. **Transport-agnostic semantics.** Refusal/error envelopes (`policy_blocked`, `scope_denied`, `action_guard`, etc.) are identical across transports.
12. **Perception is structural truth; vision is fallback.** UI tree first, escalate to screenshot/CV only when needed.
13. **Stale handles are refused.** Actions on outdated `som_id`/generation must fail closed and ask for re-observation.
14. **Best-effort side effects never break the run.** Audit/trace/learnings/backup failures are contained.

## Privacy & data
15. **Local-first by default.** Cloud sync, B2 storage, analytics, and remote diagnostics remain opt-in.
16. **Keys and trust material are stored securely** (EncryptedSharedPreferences on device; restricted-permission files on desktop).
17. **Logs are redacted.** No raw secrets/OTP/payment data in traces, reports, or remote telemetry.
18. **User can revoke access.** Trusted devices, wake word, permissions, and sync can be disabled/revoked at any time.

## UX & reliability
19. **User control has precedence.** Pause/stop/control-lock always wins over automation; mutating tools yield immediately.
20. **Honest termination paths.** Runs end with explicit success/partial/failure/cancelled/quota/budget/time outcomes, never silent no-ops.
21. **Resume is re-verified.** Resumed runs must re-read the screen/foreground state before mutating.
22. **User-facing reports are separate from forensic logs.** Reports are concise/actionable; traces are structured/diagnostic/redacted.
23. **Permissions are surfaced with exact fix paths**, and feature gating mirrors the centralized permission registry.
24. **Offline-safe remote gating.** Kill switch/min-version/blocklist enforcement uses cached state when remote config is unavailable and must not brick offline-only functionality beyond explicit policy.
