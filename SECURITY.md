# Security

## Reporting a vulnerability

If you find a vulnerability in AURA Lite, please report it **privately**:

- Preferred: a private security advisory on the GitHub repository.
- Alternative: email the maintainers (see the GitHub profile listed in `SECURITY.md` on the
  repository's Security tab).

We aim to acknowledge within 72 hours and, unless the issue is trivial, agree a disclosure
timeline with you before publishing details. Do not open a public issue until we have shipped a fix.

## Scope

- The app (`:app`) and the MCP server (`:mcp-server` in this repo) — the supported surface.
- Provider endpoints are the user's responsibility; misuse of an LLM endpoint key is not our
  vulnerability.
- Anything involving a third-party service we do not control is out of scope.

## What we consider vulnerabilities

- A tool executes when it should be blocked (banking/payment apps, destructive actions), bypassing
  the gate.
- A permission is used for a purpose other than the one the user is shown.
- A key leaks to a log, a trace, or the network without the user's action.
- The agent acts on instructions read off the screen when the user did not authorise it to.
- The LAN MCP server accepts connections/pairings without the user's consent.

## Safe harbour

Good-faith security research that follows this policy is authorised, and we will not pursue legal
action against you. Stay away from user data you are not authorised to see; if you discover a
dataset, stop and report it instead of exfiltrating it.
