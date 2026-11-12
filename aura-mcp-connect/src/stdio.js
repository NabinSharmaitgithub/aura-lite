#!/usr/bin/env node
/**
 * Minimal stdio ↔ local daemon proxy scaffold.
 *
 * Reads newline-delimited JSON-RPC on stdin and forwards to the local MCP daemon
 * endpoint (http://127.0.0.1:<port>/mcp), writing responses to stdout.
 */
const port = process.env.AURA_MCP_PORT || "4816";
const endpoint = `http://127.0.0.1:${port}/mcp`;

let buffer = "";
process.stdin.setEncoding("utf8");
process.stdin.on("data", async (chunk) => {
  buffer += chunk;
  let idx;
  while ((idx = buffer.indexOf("\n")) >= 0) {
    const line = buffer.slice(0, idx).trim();
    buffer = buffer.slice(idx + 1);
    if (!line) continue;
    try {
      const res = await fetch(endpoint, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: line,
      });
      const text = await res.text();
      process.stdout.write(text.endsWith("\n") ? text : text + "\n");
    } catch (e) {
      process.stdout.write(JSON.stringify({ jsonrpc: "2.0", id: null, error: { code: -32000, message: String(e) } }) + "\n");
    }
  }
});
process.stdin.resume();
