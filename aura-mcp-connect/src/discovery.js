/**
 * Phone discovery: saved address → mDNS → subnet scan → manual --host.
 * Validates candidates via /aura/info (signaling) and/or /health (MCP HTTPS).
 */
import os from "node:os";
import { loadTrust } from "./trust.js";

export async function discoverPhone(args = {}) {
  if (args.host) return (await validateHost(args.host, args)) ? args.host : null;

  const saved = loadTrust()?.host;
  if (saved && (await validateHost(saved, args))) return saved;

  const mdns = await discoverMdns();
  for (const host of mdns) {
    if (await validateHost(host, args)) return host;
  }

  for (const base of localSubnets()) {
    // Small bounded scan for responsiveness; full /24 scan can be slow.
    const candidates = [];
    for (let i = 1; i <= 254; i++) candidates.push(`${base}.${i}`);
    const batchSize = 32;
    for (let i = 0; i < candidates.length; i += batchSize) {
      const batch = candidates.slice(i, i + batchSize);
      const found = await Promise.any(
        batch.map(async (host) => {
          if (await validateHost(host, args, 200)) return host;
          throw new Error("no");
        }),
      ).catch(() => null);
      if (found) return found;
    }
  }
  return null;
}

async function validateHost(host, args = {}, timeoutMs = 700) {
  const ports = [Number(args["signaling-port"] || 47821), Number(args["mcp-port"] || 8443), 443];
  for (const port of ports) {
    if (await probe(`http://${host}:${port}/aura/info`, timeoutMs)) return true;
    if (await probe(`https://${host}:${port}/health`, timeoutMs)) return true;
  }
  return false;
}

async function probe(url, timeoutMs) {
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    const res = await fetch(url, { signal: controller.signal }).catch(() => null);
    clearTimeout(timer);
    return Boolean(res && (res.ok || res.status === 401 || res.status === 403));
  } catch {
    return false;
  }
}

async function discoverMdns() {
  // mDNS/Bonjour integration point: when a Bonjour library is available,
  // browse for the AURA service name advertised by the phone.
  return [];
}

function localSubnets() {
  const out = new Set();
  for (const nets of Object.values(os.networkInterfaces())) {
    for (const net of nets || []) {
      if (net.family === "IPv4" && !net.internal) {
        const parts = net.address.split(".");
        if (parts.length === 4) out.add(parts.slice(0, 3).join("."));
      }
    }
  }
  return [...out];
}
