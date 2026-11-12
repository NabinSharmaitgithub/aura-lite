import { discoverPhone } from "./discovery.js";
import { loadTrust, saveTrust } from "./trust.js";
import { startHttpProxyDaemon } from "./http-proxy.js";
import { challengeMessage, signReconnect } from "./hmac.js";

export async function runDaemon(args = {}) {
  const trust = loadTrust() || {};
  const host = args.host || trust.host;
  if (!host) {
    throw new Error("Not paired. Run: aura-mcp-connect pair --host <ip> --pin <pin>");
  }

  const localPort = Number(args.port || process.env.AURA_MCP_PORT || 4816);
  const transport = args.transport || trust.transport || "http";

  // WebRTC phone-link transport is the target architecture; until the WebRTC
  // data-channel bridge is fully wired, the daemon can proxy MCP JSON-RPC over
  // the phone's authenticated LAN HTTPS endpoint when a bearer token is available.
  if (transport === "http") {
    const mcpPort = Number(args["mcp-port"] || trust.mcpPort || 8443);
    const scheme = args.scheme || trust.scheme || "https";
    const targetUrl = args["target-url"] || trust.targetUrl || `${scheme}://${host}:${mcpPort}/mcp`;
    const token = args.token || trust.token || process.env.AURA_MCP_TOKEN;

    if (!token) {
      console.error("No bearer token found. Pair in the AURA app and save the token, or pass --token.");
      console.error("Trust file: ~/.aura/webrtc.json (fields: host, token, mcpPort, transport=http)");
      console.error("WebRTC transport requires the phone WebRTC link; HTTP proxy mode requires LAN MCP pairing.");
      process.exit(1);
    }

    await startHttpProxyDaemon({ port: localPort, targetUrl, token, allowInsecure: args.insecure !== "false" });
    saveTrust({ host, mcpPort, transport: "http", targetUrl, lastDaemonAt: new Date().toISOString() });
    console.log(`AURA local MCP daemon listening on http://127.0.0.1:${localPort}/mcp`);
    console.log(`Proxy target: ${targetUrl}`);
    console.log("Transport: HTTP proxy (authenticated). WebRTC phone-link remains the preferred transport when available.");
    return new Promise(() => {});
  }

  // WebRTC path scaffold: emit reconnect challenge material and wait for a full link implementation.
  const clientId = args["client-id"] || trust.clientId || "desktop";
  const challenge = challengeMessage(clientId);
  const response = trust.reconnectSecret ? signReconnect({ secret: trust.reconnectSecret, clientId, nonce: challenge.nonce }) : null;
  console.log(`AURA local MCP daemon (WebRTC scaffold) on http://127.0.0.1:${localPort}/mcp`);
  console.log(`Phone target: ${host}`);
  console.log("Reconnect challenge (for protocol integration):", JSON.stringify({ challenge, responsePresent: Boolean(response) }));
  console.log("TODO: complete WebRTC signaling/data-channel bridge via /aura/info and /aura/connect.");
  await new Promise(() => {});
}
