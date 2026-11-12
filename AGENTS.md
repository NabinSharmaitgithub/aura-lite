# AURA Lite — agent and contributor guide

> Keep this file current. Update it in the same commit as any change it describes.

## What this is

AURA Lite is an on-device Android agent: a plan → act → verify loop driven by an OpenAI-compatible
streaming client, plus an MCP server in the app that the agent and external clients share. Everything
is local-network only; there is no AURA server.

## Repo layout

```
app/          the app — UI, agent runtime, accessibility, automation, MCP bridge
mcp-server/   the tool surface (ToolCatalog, McpToolRegistry) and the LAN HTTPS/JSON-RPC server
backblaze/    optional B2 file storage docs (upload/download, session backup, exports)
firebase/     optional opt-in sync (Auth, Firestore, FCM, Remote Config); off by default
website/      (forthcoming) static site for docs and download
```

## Build and test

```sh
gradle :app:assembleDebug            # debug APK
gradle :app:testDebugUnitTest        # app unit tests
gradle :mcp-server:test              # MCP server unit tests
gradle :app:assembleRelease          # release (see release notes in README; CI produces releases)
```

CI runs the secret scan, `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, apksigner
verification and artifact upload, then on `v*` tags or `workflow_dispatch` input publishes a signed
GitHub release with checksums and a certificate report.

## Conventions

- One tool contract, in `mcp-server/src/main/kotlin/com/aura/mcp/tools/ToolCatalog.kt`. Both the
  in-app agent and paired MCP clients see the same schemas.
- Never log keys. Provider keys and MCP tokens live only in `EncryptedSharedPreferences`.
- `run_steps` is the only batched tool; nested batches are rejected by the MCP server.
- The agent refuses to open banking, payment, wallet, brokerage and authenticator apps — enforced
  in `RestrictedAppsRepository.SYSTEM_BLOCKED_PACKAGES`, not by the model's judgement.
- Destructive goals require an in-app confirmation before the first mutating tool runs.
- Commits: small, focused, signed-off where possible. Run the tests above before pushing.

## Decision log

- Tool surface is hand-held in the repo, not generated. Stability beats novelty.
- No DataStore for settings; `AuraSettings` uses SharedPreferences for synchronous reads during
  composition. DataStore remains available in the catalogue.
- Firebase is strictly opt-in; the app is fully functional offline.
- `omniparser_icon_detect.onnx` is not shipped; perception is tree-first with ML Kit OCR fallback.
