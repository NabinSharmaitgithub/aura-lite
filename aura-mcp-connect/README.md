# aura-mcp-connect

Desktop bridge scaffold for AURA phone MCP.

## Modes

- **HTTP proxy mode (implemented):** runs a local MCP endpoint on loopback (`http://127.0.0.1:4816/mcp`) and forwards JSON-RPC requests to the phone's authenticated LAN HTTPS MCP endpoint (`/mcp`) using a bearer token.
- **WebRTC phone-link mode (scaffold):** target architecture for the phone signaling flow (`/aura/info`, `/aura/connect`) and data-channel transport. Reconnect HMAC challenge helpers are included, but full WebRTC signaling/media/data wiring is still pending.

## Commands

```bash
node src/cli.js info
node src/cli.js pair --host <phone-ip> --mcp-port 8443 --token <bearer-token>
node src/cli.js daemon --transport http --mcp-port 8443 --token <bearer-token>
```

## Trust store

`~/.aura/webrtc.json` stores host/transport/token/client metadata with restrictive permissions (`0700` dir, `0600` file).

## Notes

- LAN HTTPS endpoints may use a self-signed certificate; the proxy allows insecure TLS for LAN targets by default.
- Discovery is best-effort: saved host → (mDNS hook) → bounded subnet scan.
- Full WebRTC proxying, production mDNS, SAS verification UX, and `aura-adb` workflow integration remain to be completed.
