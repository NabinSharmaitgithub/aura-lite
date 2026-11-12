#!/usr/bin/env node
/**
 * aura-mcp-connect CLI.
 *
 * Commands:
 *   daemon  - run local MCP daemon/proxy on loopback
 *   pair    - discover/pair and store trust material
 *   info    - print discovery/target info
 */
import { discoverPhone } from "./discovery.js";
import { loadTrust, saveTrust } from "./trust.js";
import { runDaemon } from "./daemon.js";

const [command = "daemon", ...rest] = process.argv.slice(2);
const args = parseArgs(rest);

async function main() {
  switch (command) {
    case "pair": {
      const host = args.host || (await discoverPhone(args));
      if (!host) {
        console.error("No phone found. Pass --host <ip> or enable discovery.");
        process.exit(1);
      }
      const trust = saveTrust({
        host,
        mcpPort: Number(args["mcp-port"] || 8443),
        transport: args.transport || "http",
        token: args.token || undefined,
        clientId: args["client-id"] || "desktop",
        pairedAt: new Date().toISOString(),
        pin: args.pin || undefined,
      });
      console.log(`Paired target saved for ${host}. Trust stored in ~/.aura/webrtc.json`);
      if (!trust.token) {
        console.log("Note: HTTP proxy mode needs a bearer token from the phone's MCP pairing flow (--token).");
      }
      return;
    }
    case "info": {
      const host = args.host || loadTrust()?.host || (await discoverPhone(args));
      console.log(JSON.stringify({ host: host || null, trust: loadTrust() || null }, null, 2));
      return;
    }
    case "daemon":
      return runDaemon(args);
    default:
      console.error(`Unknown command: ${command}`);
      process.exit(1);
  }
}

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith("--")) {
      const key = a.slice(2);
      const value = argv[i + 1] && !argv[i + 1].startsWith("--") ? argv[++i] : "true";
      out[key] = value;
    }
  }
  return out;
}

main().catch((e) => {
  console.error(e?.stack || String(e));
  process.exit(1);
});
