/**
 * Pairing crypto for the PC ↔ phone handshake (protocol 3).
 *
 * Byte-identical contract to
 * `mcp-server/src/main/kotlin/com/aura/mcp/bridge/PairingCrypto.kt`.
 */
import crypto from "node:crypto";

export const PROTOCOL_VERSION = 3;

const FINGERPRINT = /^a=fingerprint:sha-256\s+([0-9A-Fa-f:]+)\s*$/m;

/** The `a=fingerprint:sha-256` value of an SDP, upper-cased, or null when absent. */
export function extractFingerprint(sdp) {
  const match = FINGERPRINT.exec(String(sdp || ""));
  return match ? match[1].toUpperCase() : null;
}

/** The string both sides authenticate. Order is fixed: phone first, PC second. */
export function binding(phoneFingerprint, pcFingerprint) {
  return `aura-pair-v2|${phoneFingerprint}|${pcFingerprint}`;
}

/** Hex sha256 commitment to a nonce; sent before the nonce is revealed. */
export function commitment(nonce) {
  return sha256Hex(`aura-commit-v3|${nonce}`);
}

/** 6-digit code shown on both screens during first pairing. */
export function verificationCode(bindingValue, pcNonce, phoneNonce) {
  const digest = sha256(`aura-sas-v3|${bindingValue}|${pcNonce}|${phoneNonce}`);
  const n = ((digest[0] << 24) >>> 0) + (digest[1] << 16) + (digest[2] << 8) + digest[3];
  return String(n % 1_000_000).padStart(6, "0");
}

/** Public id of a pairing token: first 16 hex chars of sha256(token). */
export function tokenHash(token) {
  return sha256Hex(token).slice(0, 16);
}

/** Hex HMAC-SHA256(key = token, message = "$binding|$nonce"). */
export function proofMac(token, bindingValue, nonce) {
  return crypto.createHmac("sha256", token).update(`${bindingValue}|${nonce}`).digest("hex");
}

/** Constant-time comparison for proof MACs / commitments. */
export function macEquals(a, b) {
  try {
    const left = Buffer.from(String(a));
    const right = Buffer.from(String(b));
    return left.length === right.length && crypto.timingSafeEqual(left, right);
  } catch {
    return false;
  }
}

/** 128-bit random nonce, hex. */
export function newNonce(bytes = 16) {
  return crypto.randomBytes(bytes).toString("hex");
}

/** Uniform 6-digit pairing PIN. */
export function newPin() {
  return String(crypto.randomInt(0, 1_000_000)).padStart(6, "0");
}

export function newToken() {
  return crypto.randomBytes(32).toString("hex");
}

function sha256(input) {
  return crypto.createHash("sha256").update(Buffer.from(input, "utf8")).digest();
}

function sha256Hex(input) {
  return crypto.createHash("sha256").update(Buffer.from(input, "utf8")).digest("hex");
}
