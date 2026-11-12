package com.aura.mcp.bridge

import android.content.Context
import android.util.Log
import com.aura.mcp.lan.ConnectAuthorizer
import com.aura.mcp.lan.LanNetwork
import com.aura.mcp.lan.LanPolicy
import com.aura.mcp.lan.LanSignalingServer
import com.aura.mcp.lan.PinGate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * WebRTC DataChannel transport for LAN MCP (ported/adapted for aura-lite parity).
 * Carries MCP bytes after pairing/approval handshake over local network only.
 */
class WebRtcTransport(
    private val context: Context,
    private val connectionTracker: ConnectionTracker? = null,
    private val trustedClients: TrustedClientsStore,
    private val auditLogger: McpAuditLogger? = null,
) {
    private val TAG = "WebRtcTransport"

    private val peerConnectionFactory: PeerConnectionFactory

    @Volatile private var closed = false

    var onBytesReceived: ((ByteArray) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    data class LanStatus(val pin: String, val port: Int, val addresses: List<String>)
    var onStatusChanged: ((LanStatus) -> Unit)? = null

    data class ApprovedClient(val tokenId: String, val name: String, val token: String, val toolsets: Set<String>)
    var onNewClient: ((ApprovedClient) -> Unit)? = null

    var onApprovalRequested: ((version: String, clientName: String, host: String?, platform: String?, verificationCode: String, onApproved: (Boolean) -> Unit) -> Unit)? = null

    private val prefs = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
    val deviceId: String = prefs.getString("webrtc_device_id", null) ?: java.util.UUID.randomUUID().toString()
        .also { prefs.edit().putString("webrtc_device_id", it).apply() }

    private val network = LanNetwork(context)
    private val pinGate = PinGate(PairingCrypto::newPin)
    private val authorizer = ConnectAuthorizer(pinGate) { hash ->
        trustedTokens().any { PairingCrypto.tokenHash(it) == hash }
    }
    private var server: LanSignalingServer? = null

    private val connectLock = Any()
    private val stateLock = Any()
    @Volatile private var active: Peer? = null
    @Volatile private var pending: Peer? = null
    private val worker = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "aura-webrtc-peer").apply { isDaemon = true }
    }

    private data class ClientMeta(val version: String, val name: String, val host: String?, val platform: String?)
    private data class PendingChallenge(val token: String, val nonce: String, val binding: String, val meta: ClientMeta)
    private data class PendingSas(
        val tokenHash: String,
        val commit: String,
        val phoneNonce: String,
        val binding: String,
        val meta: ClientMeta,
    )
    private data class PendingEnroll(val tokenHash: String, val meta: ClientMeta)
    private data class ClientRecord(val tokenId: String, val meta: ClientMeta, val sessionKey: String?, val token: String)

    private inner class Peer(val auth: ConnectAuthorizer.Decision, val address: String?) {
        @Volatile var pc: PeerConnection? = null
        @Volatile var dataChannel: DataChannel? = null
        @Volatile var approved = false
        @Volatile var initSeen = false
        @Volatile var challenge: PendingChallenge? = null
        @Volatile var sas: PendingSas? = null
        @Volatile var enroll: PendingEnroll? = null
        @Volatile var client: ClientRecord? = null
        @Volatile var dead = false
        val gathered = CompletableFuture<Unit>()

        fun binding(): String? {
            val phoneFp = PairingCrypto.extractFingerprint(pc?.localDescription?.description)
            val pcFp = PairingCrypto.extractFingerprint(pc?.remoteDescription?.description)
            return if (phoneFp != null && pcFp != null) PairingCrypto.binding(phoneFp, pcFp) else null
        }

        fun send(message: String) {
            runCatching { dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap(message.toByteArray()), false)) }
        }

        fun close() {
            if (dead) return
            dead = true
            val dc = dataChannel
            val p = pc
            onWorker {
                runCatching { dc?.close() }
                runCatching { p?.close() }
            }
        }
    }

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions(),
        )
        val options = PeerConnectionFactory.Options().apply {
            networkIgnoreMask = (1 shl 2) or (1 shl 6) or (1 shl 7) or (1 shl 8) or (1 shl 9)
        }
        peerConnectionFactory = PeerConnectionFactory.builder().setOptions(options).createPeerConnectionFactory()
    }

    fun startListening(): LanStatus {
        val s = LanSignalingServer(
            handler = object : LanSignalingServer.Handler {
                override fun info() = buildJsonObject {
                    put("service", "aura-mcp")
                    put("proto", PairingCrypto.PROTOCOL_VERSION)
                    put("deviceId", deviceId)
                }
                override fun connect(request: LanSignalingServer.ConnectRequest) = onConnect(request)
            },
            acceptPeer = { socket -> LanPolicy.isLanPeer(socket.inetAddress) && !network.arrivedOverCellular(socket) },
            log = { Log.i(TAG, it) },
        )
        val port = s.start()
        server = s
        pinGate.onPinChanged = { publishStatus() }
        network.advertise(port, deviceId)
        network.watch {
            if (closed) return@watch
            network.advertise(port, deviceId)
            publishStatus()
        }
        return status()
    }

    private fun status() = LanStatus(pinGate.pin, server?.port ?: 0, network.lanAddresses())
    private fun publishStatus() { if (!closed) onStatusChanged?.invoke(status()) }

    private fun onConnect(request: LanSignalingServer.ConnectRequest): LanSignalingServer.Response {
        if (closed) return LanSignalingServer.Response.error(503, "stopped", "MCP is stopped on the phone. Start it in AURA -> MCP Center.")
        val decision = authorizer.authorize(request)
        if (decision is ConnectAuthorizer.Decision.Refused) return decision.response
        val offer = LanPolicy.filterSdp(request.offerSdp)
        if (LanPolicy.lanCandidateCount(offer) == 0) {
            return LanSignalingServer.Response.error(409, "no_lan", "Your computer offered no local-network address. Connect it to the same Wi-Fi as the phone.")
        }
        val peer = Peer(decision, request.peer.hostAddress)
        val answer = synchronized(connectLock) {
            if (replacePending(peer)) answerOffer(peer, offer) else null
        }
        if (answer == null) {
            drop(peer)
            return LanSignalingServer.Response.error(503, "busy", "The phone couldn't set up the connection. Try again.")
        }
        if (LanPolicy.lanCandidateCount(answer) == 0) {
            drop(peer)
            return LanSignalingServer.Response.error(409, "no_lan", "The phone has no local-network address. Connect it to Wi-Fi and try again.")
        }
        return LanSignalingServer.Response(200, buildJsonObject { putJsonObject("answer") { put("type", "answer"); put("sdp", answer) } })
    }

    private fun replacePending(peer: Peer): Boolean {
        val old = synchronized(stateLock) {
            if (closed) return false
            pending.also { pending = peer }
        }
        old?.close()
        return true
    }

    private fun answerOffer(peer: Peer, offerSdp: String): String? {
        val pc = createPeerConnection(peer) ?: return null
        val localSet = CompletableFuture<Unit>()
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(p0: SessionDescription?) {
                        val desc = p0 ?: run { localSet.completeExceptionally(IllegalStateException("no answer")); return }
                        pc.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() { localSet.complete(Unit) }
                            override fun onSetFailure(p0: String?) { localSet.completeExceptionally(IllegalStateException(p0)) }
                        }, desc)
                    }
                    override fun onCreateFailure(p0: String?) { localSet.completeExceptionally(IllegalStateException(p0)) }
                }, MediaConstraints())
            }
            override fun onSetFailure(p0: String?) { localSet.completeExceptionally(IllegalStateException(p0)) }
        }, SessionDescription(SessionDescription.Type.OFFER, offerSdp))
        try { localSet.get(5, TimeUnit.SECONDS) } catch (e: Exception) { Log.w(TAG, "Could not answer offer: ${e.message}"); return null }
        runCatching { peer.gathered.get(3, TimeUnit.SECONDS) }
        val sdp = pc.localDescription?.description ?: return null
        return LanPolicy.filterSdp(sdp)
    }

    private fun createPeerConnection(peer: Peer): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
        val pc = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                when (state) {
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.CLOSED -> drop(peer)
                    PeerConnection.IceConnectionState.DISCONNECTED -> onWorker(ICE_GRACE_MS) {
                        val now = runCatching { peer.pc?.iceConnectionState() }.getOrNull()
                        if (!peer.dead && now == PeerConnection.IceConnectionState.DISCONNECTED) drop(peer)
                    }
                    else -> {}
                }
            }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                if (p0 == PeerConnection.IceGatheringState.COMPLETE) peer.gathered.complete(Unit)
            }
            override fun onIceCandidate(candidate: IceCandidate?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {
                if (dc == null || peer.dead) return
                peer.dataChannel = dc
                observe(peer, dc)
            }
            override fun onRenegotiationNeeded() {}
        }) ?: return null
        peer.pc = pc
        return pc
    }

    private fun drop(peer: Peer) {
        val wasActive = synchronized(stateLock) {
            when {
                active === peer -> { active = null; true }
                pending === peer -> { pending = null; false }
                else -> false
            }
        }
        peer.close()
        if (wasActive) clientGone(peer)
    }

    private fun clientGone(peer: Peer) {
        onDisconnected?.invoke()
        peer.client?.let { c ->
            auditLogger?.logConnection(ConnectionEventType.DISCONNECTED, c.tokenId, c.meta.name, c.meta.host, c.meta.platform)
            c.sessionKey?.let { connectionTracker?.onDisconnect(it) }
        }
    }

    private fun observe(peer: Peer, dc: DataChannel) {
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() {
                if (runCatching { dc.state() }.getOrNull() == DataChannel.State.CLOSED) drop(peer)
            }
            override fun onMessage(buffer: DataChannel.Buffer?) {
                if (buffer == null || peer.dead) return
                val data = ByteArray(buffer.data.remaining()).also { buffer.data.get(it) }
                when {
                    !peer.approved -> handleControlMessage(peer, String(data))
                    peer === active -> onBytesReceived?.invoke(data)
                }
            }
        })
    }

    private fun handleControlMessage(peer: Peer, text: String) {
        val json = try { org.json.JSONObject(text) } catch (e: Exception) { return }
        when (json.optString("type")) {
            "init" -> if (!peer.initSeen) { peer.initSeen = true; onInit(peer, json) }
            "proof" -> onProof(peer, json.optString("mac"))
            "reveal" -> onReveal(peer, json.optString("nonce"))
            "token" -> onToken(peer, json.optString("clientToken"))
        }
    }

    private fun onInit(peer: Peer, json: org.json.JSONObject) {
        val meta = ClientMeta(
            version = json.optString("version", "unknown"),
            name = json.optString("client", "Unknown Client"),
            host = json.optString("host", "").takeIf { it.isNotEmpty() },
            platform = json.optString("platform", "").takeIf { it.isNotEmpty() },
        )
        val proto = json.optInt("proto", 1)
        if (proto < MIN_RECONNECT_PROTOCOL) { deny(peer, meta, null, UPDATE_BRIDGE); return }
        val binding = peer.binding()
        if (binding == null) { deny(peer, meta, null, "Could not read the connection fingerprints"); return }
        val auth = peer.auth
        val tokenHash = json.optString("tokenHash", "")
        if (auth is ConnectAuthorizer.Decision.Token && auth.tokenHash != tokenHash) {
            deny(peer, meta, null, "Pairing token did not match"); return
        }
        val token = if (tokenHash.isEmpty()) null else trustedTokens().firstOrNull { PairingCrypto.tokenHash(it) == tokenHash }
        if (token != null) {
            val nonce = PairingCrypto.newNonce()
            peer.challenge = PendingChallenge(token, nonce, binding, meta)
            peer.send(org.json.JSONObject().put("type", "challenge").put("nonce", nonce).toString())
            return
        }
        if (auth != ConnectAuthorizer.Decision.Pin) {
            deny(peer, meta, null, "This computer isn't paired. Run: aura-mcp pair <PIN from AURA>"); return
        }
        if (proto < PairingCrypto.PROTOCOL_VERSION) { deny(peer, meta, null, UPDATE_BRIDGE); return }
        val commit = json.optString("commit", "")
        if (tokenHash.length != 16 || !HEX_SHA256.matches(commit)) {
            deny(peer, meta, null, "Missing pairing token"); return
        }
        val nonce = PairingCrypto.newNonce()
        peer.sas = PendingSas(tokenHash, commit, nonce, binding, meta)
        peer.send(org.json.JSONObject().put("type", "sas").put("nonce", nonce).toString())
    }

    private fun onReveal(peer: Peer, pcNonce: String) {
        val s = peer.sas ?: return
        peer.sas = null
        if (!PairingCrypto.macEquals(PairingCrypto.commitment(pcNonce), s.commit)) {
            deny(peer, s.meta, null, "Pairing commitment did not match"); return
        }
        val code = PairingCrypto.verificationCode(s.binding, pcNonce, s.phoneNonce)
        onApprovalRequested?.invoke(s.meta.version, s.meta.name, s.meta.host, s.meta.platform, code) { approved ->
            if (peer.dead || peer !== pending) return@invoke
            if (approved) {
                peer.enroll = PendingEnroll(s.tokenHash, s.meta)
                peer.send("{\"type\":\"enroll\"}")
            } else {
                deny(peer, s.meta, s.tokenHash.take(8), "Denied on the phone")
            }
        } ?: deny(peer, s.meta, null, "No approval UI available")
    }

    private fun onProof(peer: Peer, mac: String) {
        val c = peer.challenge ?: return
        peer.challenge = null
        if (!PairingCrypto.macEquals(PairingCrypto.proofMac(c.token, c.binding, c.nonce), mac)) {
            deny(peer, c.meta, c.token.take(8), "Pairing proof did not match"); return
        }
        if (!promote(peer, c.token, c.meta)) return
        trustedClients.touch(c.token, c.meta.name, c.meta.host, c.meta.platform, System.currentTimeMillis())
        auditLogger?.logConnection(ConnectionEventType.AUTO_APPROVED, c.token.take(8), c.meta.name, c.meta.host, c.meta.platform)
    }

    private fun onToken(peer: Peer, clientToken: String) {
        val e = peer.enroll ?: return
        peer.enroll = null
        if (clientToken.length < 16 || PairingCrypto.tokenHash(clientToken) != e.tokenHash) {
            deny(peer, e.meta, null, "Pairing token did not match"); return
        }
        if (!promote(peer, clientToken, e.meta)) return
        trustedClients.approve(clientToken, e.meta.name, e.meta.host, e.meta.platform, System.currentTimeMillis())
        auditLogger?.logConnection(ConnectionEventType.APPROVED, clientToken.take(8), e.meta.name, e.meta.host, e.meta.platform)
    }

    private fun promote(peer: Peer, token: String, meta: ClientMeta): Boolean {
        var previous: Peer? = null
        val promoted = synchronized(stateLock) {
            if (closed || peer.dead || pending !== peer) return@synchronized false
            pending = null
            previous = active
            active = peer
            true
        }
        if (!promoted) { drop(peer); return false }
        previous?.let { it.close(); clientGone(it) }
        val tokenId = token.take(8)
        val toolsets = trustedClients.clients.value.firstOrNull { it.token == token }?.enabledToolsets ?: setOf("CORE", "ASSISTANT", "BROWSER")
        onNewClient?.invoke(ApprovedClient(tokenId, meta.name, token, toolsets))
        peer.approved = true
        peer.client = ClientRecord(
            tokenId = tokenId,
            meta = meta,
            sessionKey = connectionTracker?.onConnect(
                ClientInfo(tokenId, meta.name, peer.address ?: meta.host, meta.version, meta.host, meta.platform, "WebRTC (LAN)")
            ),
            token = token,
        )
        peer.send("{\"type\":\"approved\"}")
        return true
    }

    private fun deny(peer: Peer, meta: ClientMeta, tokenId: String?, reason: String) {
        auditLogger?.logConnection(ConnectionEventType.DENIED, tokenId ?: "webrtc", meta.name, meta.host, meta.platform)
        peer.challenge = null; peer.sas = null; peer.enroll = null
        peer.send(org.json.JSONObject().put("type", "denied").put("reason", reason).toString())
        onWorker(DENY_FLUSH_MS) { drop(peer) }
    }

    private fun trustedTokens(): List<String> = trustedClients.clients.value.map { it.token }

    private fun onWorker(delayMs: Long = 0, block: () -> Unit) {
        runCatching { worker.schedule(block, delayMs, TimeUnit.MILLISECONDS) }
    }

    fun sendFrame(bytes: ByteArray) {
        val dc = active?.dataChannel ?: return
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + MAX_DATACHANNEL_CHUNK, bytes.size)
            dc.send(DataChannel.Buffer(ByteBuffer.wrap(bytes.copyOfRange(offset, end)), true))
            offset = end
        }
    }

    fun close() {
        val peers = synchronized(stateLock) {
            if (closed) return
            closed = true
            listOfNotNull(pending, active)
        }
        runCatching { server?.stop() }
        server = null
        network.unwatch()
        network.stopAdvertising()
        peers.forEach(::drop)
        worker.shutdown()
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }

    companion object {
        private const val MAX_DATACHANNEL_CHUNK = 16 * 1024
        private const val ICE_GRACE_MS = 10_000L
        private const val DENY_FLUSH_MS = 500L
        private const val MIN_RECONNECT_PROTOCOL = 2
        private val HEX_SHA256 = Regex("^[0-9a-f]{64}$")
        const val MIN_BRIDGE_VERSION = "0.10.0"
        private const val UPDATE_BRIDGE =
            "This AURA needs aura-mcp-connect $MIN_BRIDGE_VERSION or newer. Update it with: npm i -g aura-mcp-connect@latest"
    }
}
