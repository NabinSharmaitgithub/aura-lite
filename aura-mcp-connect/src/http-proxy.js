import http from "node:http";
import https from "node:https";

export function startHttpProxyDaemon({ port = 4816, targetUrl, token, allowInsecure = true }) {
  const agent = targetUrl.startsWith("https:")
    ? new https.Agent({ rejectUnauthorized: !allowInsecure ? true : false })
    : undefined;

  const server = http.createServer(async (req, res) => {
    if (req.method !== "POST" || (req.url !== "/mcp" && req.url !== "/")) {
      res.writeHead(404, { "content-type": "application/json" });
      res.end(JSON.stringify({ error: "not_found" }));
      return;
    }
    let body = "";
    req.on("data", (c) => (body += c));
    req.on("end", async () => {
      try {
        const upstream = await fetch(targetUrl, {
          method: "POST",
          headers: {
            "content-type": "application/json",
            ...(token ? { authorization: `Bearer ${token}` } : {}),
          },
          body,
          // Node 20+ global fetch; allow self-signed for LAN HTTPS via dispatcher is limited,
          // so we fall back to node:http(s) when needed.
        }).catch(() => null);

        if (upstream) {
          const text = await upstream.text();
          res.writeHead(upstream.status, { "content-type": "application/json" });
          res.end(text);
          return;
        }

        // Fallback for environments where fetch can't ignore self-signed certs.
        const out = await forwardNode({ targetUrl, body, token, agent });
        res.writeHead(out.status, { "content-type": "application/json" });
        res.end(out.body);
      } catch (e) {
        res.writeHead(502, { "content-type": "application/json" });
        res.end(JSON.stringify({ jsonrpc: "2.0", id: null, error: { code: -32000, message: String(e) } }));
      }
    });
  });

  return new Promise((resolve, reject) => {
    server.on("error", reject);
    server.listen(port, "127.0.0.1", () => resolve(server));
  });
}

function forwardNode({ targetUrl, body, token, agent }) {
  return new Promise((resolve, reject) => {
    const url = new URL(targetUrl);
    const lib = url.protocol === "https:" ? https : http;
    const req = lib.request(
      {
        protocol: url.protocol,
        hostname: url.hostname,
        port: url.port,
        path: url.pathname,
        method: "POST",
        headers: {
          "content-type": "application/json",
          "content-length": Buffer.byteLength(body),
          ...(token ? { authorization: `Bearer ${token}` } : {}),
        },
        agent,
        rejectUnauthorized: false,
      },
      (res) => {
        let data = "";
        res.on("data", (c) => (data += c));
        res.on("end", () => resolve({ status: res.statusCode || 502, body: data }));
      },
    );
    req.on("error", reject);
    req.write(body);
    req.end();
  });
}
