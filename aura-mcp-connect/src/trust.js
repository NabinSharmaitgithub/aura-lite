/** Trust material storage for the desktop bridge (~/.aura/webrtc.json). */
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const dir = path.join(os.homedir(), ".aura");
const file = path.join(dir, "webrtc.json");

export function loadTrust() {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    return null;
  }
}

export function saveTrust(value) {
  fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
  const existing = loadTrust() || {};
  const merged = { ...existing, ...stripUndefined(value) };
  fs.writeFileSync(file, JSON.stringify(merged, null, 2), { mode: 0o600 });
  try {
    fs.chmodSync(file, 0o600);
  } catch {}
  return merged;
}

export function clearTrust() {
  try {
    fs.unlinkSync(file);
  } catch {}
}

function stripUndefined(obj) {
  const out = {};
  for (const [k, v] of Object.entries(obj || {})) {
    if (v !== undefined) out[k] = v;
  }
  return out;
}
