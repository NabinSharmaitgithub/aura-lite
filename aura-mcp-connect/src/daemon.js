import { discoverPhone } from "./discovery.js";
import { loadTrust, saveTrust } from "./trust.js";
import { startHttpProxyDaemon } from "./http-proxy.js";
import { startWebRtcProxyDaemon } from "./webrtc-proxy.js";
import { WebRtcLink, hasWebRtc } from "./webrtc.js";

export async function runDaemon(args = {}) {
  const trust = loadTrust() || {};
  const host = args.host || trust.host;
  if (!host) {
    throw new Error("Not paired. Run: aura-mcp-connect pair --host <ip> --pin <pin>");
  }

  const localPort = Number(args.port || process.env.AURA_MCP_PORT || 4816);
  const transport = args.transport || trust.transport || (await preferredTransport());

  if (transport === "http") {
    return runHttpDaemon({ args, trust, host, localPort });
  }
  return runWebRtcDaemon({ args, trust, host, localPort });
}

async function preferredTransport() {
  return (await hasWebRtc()) ? "webrtc" : "http";
}

async function runHttpDaemon({ args, trust, host, localPort }) {
  const mcpPort = Number(args["mcp-port"] || trust.mcpPort || 8443);
  const scheme = args.scheme || trust.scheme || "https";
  const targetUrl = args["target-url"] || trust.targetUrl || `${scheme}://${host}:${mcpPort}/mcp`;
  const token = args.token || trust.token || process.env.AURA_MCP_TOKEN;

  if (!token) {
    console.error("No bearer token found. Pair in the AURA app and save the token, or pass --token.");
    console.error("Trust file: ~/.aura/webrtc.json (fields: host, token, mcpPort, transport=http)");
    console.error("Install a WebRTC implementation (npm i node-datachannel) to use the peer-to-peer transport.");
    process.exit(1);
  }

  await startHttpProxyDaemon({ port: localPort, targetUrl, token, allowInsecure: args.insecure !== "false" });
  saveTrust({ host, mcpPort, transport: "http", targetUrl, lastDaemonAt: new Date().toISOString() });
  console.log(`AURA local MCP daemon listening on http://127.0.0.1:${localPort}/mcp`);
  console.log(`Proxy target: ${targetUrl}`);
  console.log("Transport: HTTP proxy (authenticated).");
  return new Promise(() => {});
}

async function runWebRtcDaemon({ args, trust, host, localPort }) {
  if (!(await hasWebRtc())) {
    console.error("WebRTC transport selected but no implementation is installed.");
    console.error("Install one with: npm i node-datachannel");
    console.error("Then retry, or use --transport http for the authenticated LAN HTTPS proxy.");
    process.exit(1);
  }

  const signalingPort = Number(
    args["signaling-port"] || trust.signalingPort || process.env.AURA_SIGNALING_PORT || 47821,
  );
  const pin = args.pin || trust.pin || process.env.AURA_PIN;
  const token = args.token || trust.token || process.env.AURA_MCP_TOKEN;
  if (!pin && !token) {
    console.error("Not paired and no PIN supplied. Run: aura-mcp-connect pair --host <ip> --pin <pin>");
    process.exit(1);
  }

  const clientId = args["client-id"] || trust.clientId || "desktop";
  const link = new WebRtcLink({
    host,
    signalingPort,
    clientId,
    clientName: args.name || trust.clientName || "aura-mcp-connect",
    pin: token ? undefined : pin,
    token,
    insecure: args.insecure !== "false",
  });

  link.on("verification", ({ code }) => {
    console.log(`Verify this code on the phone: ${code}`);
  });

  console.log(`Connecting to the phone over WebRTC at ${host}:${signalingPort} …`);
  try {
    await link.connect(Number(args["connect-timeout"] || 30_000));
  } catch (error) {
    console.error(`WebRTC connection failed: ${error?.message || error}`);
    console.error("The phone may be unreachable, the PIN wrong, or the phone may still be showing the approval prompt.");
    process.exit(1);
  }

  await startWebRtcProxyDaemon({ port: localPort, link });
  saveTrust({
    host,
    signalingPort,
    transport: "webrtc",
    token: link.token,
    clientId,
    pairedAt: trust.pairedAt || new Date().toISOString(),
    lastDaemonAt: new Date().toISOString(),
  });
  console.log(`AURA local MCP daemon listening on http://127.0.0.1:${localPort}/mcp`);
  console.log(`Transport: WebRTC DataChannel → ${host}:${signalingPort} (LAN only).`);
  return new Promise(() => {});
}
