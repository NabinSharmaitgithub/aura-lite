/**
 * Phone discovery: saved address → mDNS → subnet scan → manual --host.
 * Validates candidates via /aura/info (signaling) and/or /health (MCP HTTPS).
 */
import os from "node:os";
import dgram from "node:dgram";
import { execFileSync } from "node:child_process";
import { loadTrust } from "./trust.js";

const MDNS_GROUP = "224.0.0.251";
const MDNS_PORT = 5353;
const AURA_SERVICES = ["_aura._tcp.local", "_http._tcp.local", "_https._tcp.local"];

export async function discoverPhone(args = {}) {
  if (args.host) return (await validateHost(args.host, args)) ? args.host : null;

  const saved = loadTrust()?.host;
  if (saved && (await validateHost(saved, args))) return saved;

  const mdns = await discoverMdns(Number(args["mdns-timeout"] || 1800));
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

/**
 * Discover the phone over mDNS/Bonjour. First tries the local `dns-sd`/`avahi-browse`
 * tools when present, then falls back to a dependency-free multicast query.
 */
export async function discoverMdns(timeoutMs = 1800) {
  const hosts = new Set();
  for (const host of discoverViaCli()) hosts.add(host);
  for (const host of await discoverViaMulticast(timeoutMs)) hosts.add(host);
  return [...hosts];
}

function discoverViaCli() {
  const out = [];
  for (const tool of ["avahi-browse", "dns-sd"]) {
    try {
      const raw =
        tool === "avahi-browse"
          ? execFileSync("avahi-browse", ["-tpr", "_aura._tcp"], { encoding: "utf8", timeout: 2500 })
          : execFileSync("dns-sd", ["-B", "_aura._tcp", "local"], { encoding: "utf8", timeout: 2500 });
      for (const match of raw.matchAll(/(\d{1,3}(?:\.\d{1,3}){3})/g)) out.push(match[1]);
    } catch {
      // Tool not installed or timed out; the multicast query is the primary path.
    }
  }
  return out;
}

function discoverViaMulticast(timeoutMs) {
  return new Promise((resolve) => {
    const hosts = new Set();
    let socket;
    let timer;
    let done = false;
    const finish = () => {
      if (done) return;
      done = true;
      if (timer) clearInterval(timer);
      try {
        socket?.close();
      } catch {}
      resolve([...hosts]);
    };
    try {
      socket = dgram.createSocket({ type: "udp4", reuseAddr: true });
      socket.on("error", finish);
      socket.on("message", (msg) => {
        for (const host of parseMdnsResponse(msg)) hosts.add(host);
      });
      socket.bind(0, () => {
        try {
          socket.addMembership(MDNS_GROUP);
        } catch {}
        const send = () => {
          for (const service of AURA_SERVICES) {
            try {
              socket.send(buildMdnsQuery(service), MDNS_PORT, MDNS_GROUP);
            } catch {}
          }
        };
        send();
        timer = setInterval(send, 450);
      });
      setTimeout(finish, timeoutMs);
    } catch {
      finish();
    }
  });
}

function buildMdnsQuery(name) {
  const header = Buffer.alloc(12);
  header.writeUInt16BE(0, 0); // transaction id (mDNS uses 0)
  header.writeUInt16BE(0, 2); // standard query
  header.writeUInt16BE(1, 4); // one question
  const question = encodeName(name);
  const tail = Buffer.alloc(4);
  tail.writeUInt16BE(12, 0); // PTR
  tail.writeUInt16BE(1, 2); // IN
  return Buffer.concat([header, question, tail]);
}

function encodeName(name) {
  const parts = name.split(".").filter(Boolean);
  const encoded = parts.map((part) => {
    const label = Buffer.from(part, "utf8");
    return Buffer.concat([Buffer.from([label.length & 0x3f]), label]);
  });
  return Buffer.concat([...encoded, Buffer.from([0])]);
}

function readName(buffer, offset) {
  const labels = [];
  let end = offset;
  let jumped = false;
  let hops = 0;
  while (offset < buffer.length && hops < 32) {
    const len = buffer[offset];
    if (len === 0) {
      offset += 1;
      if (!jumped) end = offset;
      break;
    }
    if ((len & 0xc0) === 0xc0) {
      const pointer = ((len & 0x3f) << 8) | buffer[offset + 1];
      if (!jumped) end = offset + 2;
      offset = pointer;
      jumped = true;
      hops++;
      continue;
    }
    labels.push(buffer.toString("utf8", offset + 1, offset + 1 + len));
    offset += 1 + len;
    if (!jumped) end = offset;
  }
  return { name: labels.join("."), end };
}

/** Extract IPv4 addresses from an mDNS response (all answer/authority/additional records). */
export function parseMdnsResponse(buffer) {
  const out = [];
  if (buffer.length < 12) return out;
  const qd = buffer.readUInt16BE(4);
  const totalRecords = buffer.readUInt16BE(6) + buffer.readUInt16BE(8) + buffer.readUInt16BE(10);
  let offset = 12;
  for (let i = 0; i < qd; i++) {
    const question = readName(buffer, offset);
    offset = question.end + 4;
  }
  for (let i = 0; i < totalRecords && offset + 10 <= buffer.length; i++) {
    const record = readName(buffer, offset);
    offset = record.end;
    if (offset + 10 > buffer.length) break;
    const type = buffer.readUInt16BE(offset);
    const rdLength = buffer.readUInt16BE(offset + 8);
    offset += 10;
    if (offset + rdLength > buffer.length) break;
    if (type === 1 && rdLength === 4) {
      out.push(`${buffer[offset]}.${buffer[offset + 1]}.${buffer[offset + 2]}.${buffer[offset + 3]}`);
    }
    offset += rdLength;
  }
  return out;
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
