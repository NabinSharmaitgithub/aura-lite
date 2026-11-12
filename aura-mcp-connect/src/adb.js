#!/usr/bin/env node
/**
 * aura-adb helper: discovers the phone's LAN address over USB/adb (best-effort)
 * and prints pairing/daemon hints. Requires adb in PATH.
 */
import { execSync } from "node:child_process";

function sh(cmd) {
  try {
    return execSync(cmd, { encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] }).trim();
  } catch {
    return "";
  }
}

const serial = process.argv[2];
const prefix = serial ? `adb -s ${serial}` : "adb";
const ipRoute = sh(`${prefix} shell ip route`) || sh(`${prefix} shell ip addr show wlan0`);
const match = ipRoute.match(/src\s+(\d+\.\d+\.\d+\.\d+)/) || ipRoute.match(/inet\s+(\d+\.\d+\.\d+\.\d+)/);
if (!match) {
  console.error("Could not determine device LAN IP via adb. Ensure USB debugging is enabled and Wi-Fi is connected.");
  process.exit(1);
}
const host = match[1];
console.log(JSON.stringify({
  host,
  hint: `node src/cli.js pair --host ${host} --mcp-port 8443 --token <token>`,
  daemon: `node src/cli.js daemon --host ${host} --transport http --mcp-port 8443 --token <token>`,
}, null, 2));
