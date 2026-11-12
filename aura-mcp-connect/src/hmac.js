import crypto from "node:crypto";

/**
 * HMAC helpers for reconnect/challenge verification (scaffold-compatible).
 * The phone and desktop must agree on the shared secret derivation; until the
 * full WebRTC protocol is wired, this provides the primitives and message shapes.
 */
export function hmacSha256(key, data) {
  return crypto.createHmac("sha256", key).update(data).digest("hex");
}

export function randomNonce(bytes = 16) {
  return crypto.randomBytes(bytes).toString("hex");
}

export function challengeMessage(clientId) {
  return { type: "reconnect_challenge", clientId, nonce: randomNonce(), ts: Date.now() };
}

export function verifyChallengeResponse({ secret, nonce, response, maxAgeMs = 60_000, ts }) {
  if (!secret || !nonce || !response) return false;
  if (typeof ts === "number" && Math.abs(Date.now() - ts) > maxAgeMs) return false;
  const expected = hmacSha256(secret, nonce);
  try {
    return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(String(response)));
  } catch {
    return false;
  }
}

export function signReconnect({ secret, clientId, nonce }) {
  return hmacSha256(secret, `${clientId}:${nonce}`);
}
