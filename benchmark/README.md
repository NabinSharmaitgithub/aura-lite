# Benchmarks

AURA Lite measures itself in two modes, because "the agent works" is only believable if the
opposite is also measurable.

## Target A — on-device agent

The on-device agent is driven from a physical or emulated phone.

Prerequisites:

```sh
adb devices               # phone or emulator listed
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The simplest driver is the LAN MCP server the app hosts:

```sh
adb forward tcp:47821 tcp:47821     # USB, or use the Wi-Fi port printed in Settings → MCP
# get a token from Settings → MCP → Trusted devices
echo "$TOKEN" > /tmp/aura_token.txt
python3 tools/mcp_session.py --host 127.0.0.1 --token-file /tmp/aura_token.txt --list-tools
```

## Target B — Claude Code over MCP

Every tool in `tools/` works identically from a paired desktop client; effectiveness is measured
by running AndroidWorld's public task suite (`github.com/google-research/android_world`) against
the phone through the same MCP endpoint. Tasks are scored by AndroidWorld's own
`task.is_successful(env)` check — we do not second-guess the verdict.

## What the numbers mean

- **Success rate** across the run set, split by category (media, productivity, maps, commerce).
- **Steps to success** — mean tool invocations per completed task.
- **Human-in-the-loop interrupts** — how often the agent asked via `ask_user` instead of acting.
- **Failure modes** — the top error class, by count (e.g. `stale_screen`, `permission_denied`).

Numbers alone are not a goal; the benchmark exists so regressions are caught by the agent team,
not by users.

## Reporting results

Benchmark runs are published to the website's `benchmarks/` page. Only publish a run with:

1. The exact commit range and build variant (`release`, `arm64-v8a`).
2. Device model, Android version, and the provider/model pair used.
3. The raw per-task outcomes JSON (`benchmark/results/<date>.json`).
4. A one-paragraph human summary of what the agent struggled with.
