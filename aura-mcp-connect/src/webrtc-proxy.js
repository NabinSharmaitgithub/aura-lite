/**
 * Local MCP daemon backed by an approved WebRTC DataChannel link.
 *
 * Listens on 127.0.0.1:<port>/mcp, forwards newline-delimited JSON-RPC to the phone over the
 * data channel, and correlates responses by JSON-RPC id.
 */
import http from "node:http";

export function startWebRtcProxyDaemon({ port = 4816, link, timeoutMs = 120_000 }) {
  const pending = new Map();
  let localSeq = 0;

  link.on("message", (line) => {
    const message = safeJson(line);
    const id = message?.id;
    if (id === undefined || id === null) return;
    const key = String(id);
    const resolve = pending.get(key);
    if (resolve) {
      pending.delete(key);
      resolve(line);
    }
  });

  link.on("close", () => {
    for (const [key, resolve] of pending) {
      pending.delete(key);
      resolve(jsonRpcError(null, -32000, `WebRTC link closed while waiting for ${key}`));
    }
  });

  const server = http.createServer((req, res) => {
    if (req.method !== "POST" || (req.url !== "/mcp" && req.url !== "/")) {
      res.writeHead(404, { "content-type": "application/json" });
      res.end(JSON.stringify({ error: "not_found" }));
      return;
    }
    let body = "";
    req.on("data", (chunk) => (body += chunk));
    req.on("end", async () => {
      const request = safeJson(body);
      if (!request) {
        res.writeHead(400, { "content-type": "application/json" });
        res.end(jsonRpcError(null, -32700, "parse error"));
        return;
      }
      if (Array.isArray(request)) {
        res.writeHead(400, { "content-type": "application/json" });
        res.end(jsonRpcError(null, -32600, "batch requests are not supported over WebRTC"));
        return;
      }
      if (request.id === undefined || request.id === null) {
        // Notification: forward but do not wait for a response.
        try {
          link.send(Buffer.from(JSON.stringify(request) + "\n"));
          res.writeHead(202, { "content-type": "application/json" });
          res.end("");
        } catch (error) {
          res.writeHead(502, { "content-type": "application/json" });
          res.end(jsonRpcError(null, -32000, String(error)));
        }
        return;
      }

      const key = String(request.id);
      const responsePromise = new Promise((resolve) => pending.set(key, resolve));
      try {
        link.send(Buffer.from(JSON.stringify(request) + "\n"));
      } catch (error) {
        pending.delete(key);
        res.writeHead(502, { "content-type": "application/json" });
        res.end(jsonRpcError(request.id, -32000, String(error)));
        return;
      }
      const timer = setTimeout(() => {
        if (pending.delete(key)) resolve(jsonRpcError(request.id, -32000, "timed out waiting for the phone"));
      }, timeoutMs);
      const response = await responsePromise;
      clearTimeout(timer);
      res.writeHead(200, { "content-type": "application/json" });
      res.end(response);
    });
  });

  return new Promise((resolve, reject) => {
    server.on("error", reject);
    server.listen(port, "127.0.0.1", () => resolve(server));
  });
}

function safeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function jsonRpcError(id, code, message) {
  return JSON.stringify({ jsonrpc: "2.0", id, error: { code, message } });
}
