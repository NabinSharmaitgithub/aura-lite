# Feature-Dependent Permission Matrix

Single source of truth in code: `app/src/main/java/com/aura/aura_ui/presentation/permissions/PermissionRegistry.kt`.

| Feature | Permission / access | Required? | Fallback / behavior | Fix path |
|---|---|---|---|---|
| Speech-to-text | `RECORD_AUDIO` | Required | Voice input unavailable; text input still works | Settings → App → Permissions → Microphone |
| Wake word | `RECORD_AUDIO` + foreground microphone service | Required | Wake word disabled until granted; model must exist | Voice settings + system mic permission |
| Agent screen reading/actions | Accessibility service enabled | Required | All UI tools return `permission_denied` with fix path | Settings → Accessibility → AURA |
| Screen capture / vision | MediaProjection consent | Optional | Tree-only perception; no screenshots | In-app capture consent |
| Notifications/run controls | `POST_NOTIFICATIONS` (Android 13+) | Optional | In-app controls only | Settings → App → Notifications |
| Contacts resolution | `READ_CONTACTS` | Optional | Ask user for number/identifier | Settings → App → Permissions → Contacts |
| Calls/SMS compose | `READ_CONTACTS` (optional), telephony/SMS intents | Optional | Manual number entry | System intents may open app |
| File search/open | `READ_MEDIA_*` / storage access | Optional | Ask user to pick file/link | Settings → App → Permissions → Files/Media |
| Overlay companion / pause pill | `SYSTEM_ALERT_WINDOW` | Optional | In-app only | Settings → Display over other apps |
| MCP LAN/WebRTC | Network access; same/private LAN; pairing approval | Optional | Local in-app agent only | MCP/Trusted devices settings |
| Firebase sync/auth | Network + user opt-in + project config | Optional | Fully offline | Settings → Firebase |
| Backblaze B2 backup | Network + user-provided B2 credentials | Optional | No cloud backup | Settings → Backblaze |
| Tavily web search | Network + user API key | Optional | Agent avoids web research | Brain/Research settings |
| Exact alarms/reminders | `SCHEDULE_EXACT_ALARM` (version-dependent) | Optional | Inexact reminders or in-app reminders | System alarm settings |

## Notes
- Permission checks are enforced at runtime (bridge/tool calls), not only in UI.
- Missing permissions return structured errors with the fix path where possible.
- OEM-specific restrictions (MIUI/ColorOS/etc.) may require additional background/autostart grants for long-running wake/agent services.
