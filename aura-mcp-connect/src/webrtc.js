/**
 * WebRTC DataChannel link for the desktop bridge (computer → phone).
 *
 * Mirrors the phone protocol in `WebRtcTransport.kt`:
 *   1. POST an SDP offer to http://<phone>:<signaling-port>/aura/connect
 *   2. Set the answer, open the "mcp" DataChannel
 *   3. Reconnect: init(tokenHash) → challenge → proof(HMAC)
 *      Pairing:   init(new tokenHash + commit) → sas → reveal(nonce) → enroll → token
 *   4. After "approved", the channel carries newline-delimited JSON-RPC.
 *
 * A WebRTC implementation is loaded lazily so the HTTP proxy path keeps working on machines
 * without native WebRTC support. Install one of: node-datachannel, @roamhq/wrtc, wrtc.
 */
import { EventEmitter } from "node:events";
import os from "node:os";
import {
  PROTOCOL_VERSION,
  binding,
  commitment,
  extractFingerprint,
  newNonce,
  newToken,
  proofMac,
  tokenHash,
  verificationCode,
} from "./pairing-crypto.js";

const BRIDGE_VERSION = "0.10.0";

export async function loadWebRtc() {
  const candidates = ["node-datachannel/polyfill", "@roamhq/wrtc", "wrtc"];
  for (const name of candidates) {
    try {
      const mod = await import(name);
      const rtc = mod?.default ?? mod;
      if (typeof rtc?.RTCPeerConnection === "function") return rtc;
    } catch {
      // Optional dependency not installed; try the next implementation.
    }
  }
  if (typeof globalThis.RTCPeerConnection === "function") return globalThis;
  return null;
}

export async function hasWebRtc() {
  return (await loadWebRtc()) != null;
}

export class WebRtcLink extends EventEmitter {
  constructor({
    host,
    signalingPort = 47821,
    clientId = "desktop",
    clientName = "aura-mcp-connect",
    pin,
    token,
    insecure = true,
  } = {}) {
    super();
    this.host = host;
    this.signalingPort = Number(signalingPort);
    this.clientId = clientId;
    this.clientName = clientName;
    this.pin = pin;
    this.token = token || newToken();
    this.insecure = insecure;
    this.pc = null;
    this.dc = null;
    this.approved = false;
    this.closed = false;
    this.buffer = "";
    this.pcNonce = null;
    this.pcFingerprint = null;
    this.phoneFingerprint = null;
    this.bindingValue = null;
    this.readyPromise = null;
  }

  /** Connects and resolves once the phone approves the channel. */
  async connect(timeoutMs = 30_000) {
    if (this.readyPromise) return this.readyPromise;
    this.readyPromise = this.#connect(timeoutMs);
    return this.readyPromise;
  }

  async #connect(timeoutMs) {
    const rtc = await loadWebRtc();
    if (!rtc) {
      throw new Error(
        "No WebRTC implementation found. Install one, e.g. `npm i node-datachannel`, or use --transport http.",
      );
    }
    const RTCPeerConnection = rtc.RTCPeerConnection;
    const pc = new RTCPeerConnection({ iceServers: [] });
    this.pc = pc;

    const dc = pc.createDataChannel("mcp", { ordered: true });
    this.dc = dc;
    try {
      dc.binaryType = "arraybuffer";
    } catch {}
    this.#listen(dc);

    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    await waitForIceGathering(pc, 3500);
    const offerSdp = pc.localDescription?.sdp || offer.sdp;

    const body = {
      offer: { type: "offer", sdp: offerSdp },
      ...(this.pin ? { pin: String(this.pin) } : { tokenHash: tokenHash(this.token) }),
    };
    const response = await this.#post("/aura/connect", body);
    const answer = response?.answer;
    if (!answer?.sdp) throw new Error("The phone did not return an answer SDP");

    this.pcFingerprint = extractFingerprint(offerSdp);
    this.phoneFingerprint = extractFingerprint(answer.sdp);
    if (!this.pcFingerprint || !this.phoneFingerprint) {
      throw new Error("Could not read SDP fingerprints for pairing binding");
    }
    this.bindingValue = binding(this.phoneFingerprint, this.pcFingerprint);

    await pc.setRemoteDescription({ type: "answer", sdp: answer.sdp });

    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("Timed out opening the WebRTC data channel")), timeoutMs);
      const done = () => {
        clearTimeout(timer);
        resolve();
      };
      if (this.approved) return done();
      this.once("ready", done);
      this.once("error", (error) => {
        clearTimeout(timer);
        reject(error);
      });
    });
    return this;
  }

  /** Sends raw bytes (newline-delimited JSON-RPC) over the approved channel. */
  send(bytes) {
    if (!this.dc || !this.approved) throw new Error("WebRTC link is not approved yet");
    this.dc.send(bytes);
  }

  close() {
    if (this.closed) return;
    this.closed = true;
    try {
      this.dc?.close();
    } catch {}
    try {
      this.pc?.close();
    } catch {}
    this.emit("close");
  }

  #listen(dc) {
    const onOpen = () => this.#sendInit();
    const onMessage = (event) => this.#onData(event?.data ?? event);
    const onClose = () => {
      this.emit("close");
    };
    if (typeof dc.addEventListener === "function") {
      dc.addEventListener("open", onOpen);
      dc.addEventListener("message", onMessage);
      dc.addEventListener("close", onClose);
    } else {
      dc.onopen = onOpen;
      dc.onmessage = onMessage;
      dc.onclose = onClose;
    }
  }

  #sendInit() {
    const init = {
      type: "init",
      proto: PROTOCOL_VERSION,
      version: BRIDGE_VERSION,
      client: this.clientName,
      clientId: this.clientId,
      host: os.hostname(),
      platform: `${process.platform}/${process.arch}`,
      tokenHash: tokenHash(this.token),
    };
    if (this.pin) {
      // First pairing: commit to the PC nonce, then reveal it after the phone's nonce.
      this.pcNonce = newNonce();
      init.commit = commitment(this.pcNonce);
    }
    this.dc.send(JSON.stringify(init));
  }

  #onData(data) {
    const text = typeof data === "string" ? data : Buffer.from(data).toString("utf8");
    if (!this.approved) {
      const message = safeJson(text);
      if (!message) return;
      switch (message.type) {
        case "challenge": {
          const mac = proofMac(this.token, this.bindingValue, message.nonce);
          this.dc.send(JSON.stringify({ type: "proof", mac }));
          break;
        }
        case "sas": {
          if (!this.pcNonce) this.pcNonce = newNonce();
          const code = verificationCode(this.bindingValue, this.pcNonce, message.nonce);
          this.emit("verification", { code });
          this.dc.send(JSON.stringify({ type: "reveal", nonce: this.pcNonce }));
          break;
        }
        case "enroll": {
          this.dc.send(JSON.stringify({ type: "token", clientToken: this.token }));
          break;
        }
        case "approved": {
          this.approved = true;
          this.emit("ready", { token: this.token, clientId: this.clientId });
          break;
        }
        case "denied": {
          this.emit("error", new Error(message.reason || "The phone denied the connection"));
          this.close();
          break;
        }
        default:
          // Ignore unknown control messages before approval.
          break;
      }
      return;
    }

    this.buffer += text;
    let index;
    while ((index = this.buffer.indexOf("\n")) >= 0) {
      const line = this.buffer.slice(0, index).trim();
      this.buffer = this.buffer.slice(index + 1);
      if (line) this.emit("message", line);
    }
  }

  async #post(path, body) {
    const url = `http://${this.host}:${this.signalingPort}${path}`;
    const response = await fetch(url, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    });
    const text = await response.text();
    const parsed = safeJson(text);
    if (!response.ok) {
      const reason = parsed?.error || parsed?.message || text || `HTTP ${response.status}`;
      const code = parsed?.code ? ` (${parsed.code})` : "";
      throw new Error(`Phone refused the connection: ${reason}${code}`);
    }
    return parsed;
  }
}

function waitForIceGathering(pc, timeoutMs) {
  return new Promise((resolve) => {
    if (pc.iceGatheringState === "complete") return resolve();
    let settled = false;
    const finish = () => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      try {
        pc.removeEventListener?.("icegatheringstatechange", check);
        pc.removeEventListener?.("icecandidate", check);
      } catch {}
      resolve();
    };
    const check = () => {
      if (pc.iceGatheringState === "complete") finish();
    };
    const timer = setTimeout(finish, timeoutMs);
    try {
      pc.addEventListener?.("icegatheringstatechange", check);
      pc.addEventListener?.("icecandidate", check);
    } catch {}
  });
}

function safeJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}
