#!/usr/bin/env python3
"""Minimal reference MCP client for AURA Lite over LAN HTTPS/mTLS HTTP JSON-RPC.

Usage:
  python3 tools/mcp_session.py --host 127.0.0.1 --token-file /tmp/aura_token.txt --list-tools
  python3 tools/mcp_session.py --host 192.168.1.23 --token-file /tmp/aura_token.txt --tool read_screen
  python3 tools/mcp_session.py --host 192.168.1.23 --token-file /tmp/aura_token.txt \
      --tool set_value --args '{"index": 3, "text": "hello"}'

Get the token from Settings → MCP → Trusted devices on the phone. The endpoint is the
phone's HTTPS port (default 47821; `adb forward tcp:47821 tcp:47821` works over USB).
"""

import argparse
import base64
import json
import ssl
import sys
import urllib.request


def rpc(host, port, token, method, params=None):
    url = f"https://{host}:{port}/mcp"
    body = json.dumps({"jsonrpc": "2.0", "id": 1, "method": method, "params": params or {}}).encode()
    # The dev endpoint uses a self-signed identity; production pins by fingerprint instead.
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    req = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=30, context=ctx) as resp:
        return json.loads(resp.read().decode())


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--host", required=True)
    p.add_argument("--port", type=int, default=47821)
    g = p.add_mutually_exclusive_group()
    g.add_argument("--token-file")
    g.add_argument("--token-env", default="AURA_TOKEN")
    p.add_argument("--list-tools", action="store_true")
    p.add_argument("--tool")
    p.add_argument("--args", default="{}")
    args = p.parse_args()

    if args.token_file:
        token = open(args.token_file, "r", encoding="utf-8").read().strip()
    else:
        import os
        token = os.environ.get(args.token_env, "").strip()
    if not token:
        sys.exit("no token: pass --token-file or set AURA_TOKEN")

    if args.list_tools:
        out = rpc(args.host, args.port, token, "tools/list")
        print(json.dumps(out, indent=2)[:4000])
        return

    if args.tool:
        try:
            payload = json.loads(args.args)
        except json.JSONDecodeError as e:
            sys.exit(f"invalid --args JSON: {e}")
        out = rpc(args.host, args.port, token, "tools/call", {"name": args.tool, "arguments": payload})
        print(json.dumps(out, indent=2))
        return

    p.print_help()


if __name__ == "__main__":
    main()
