package com.aura.mcp.bridge

/**
 * Explicit phase for one WebRTC peer handshake.
 *
 * The old code kept `approved`, `initSeen`, `challenge`, `sas`, `enroll` and `dead` as independent
 * fields, so an out-of-order control message could be processed in almost any state. [PeerPhase]
 * is now authoritative and [PeerTransitions] rejects messages that do not belong to the phase.
 */
enum class PeerPhase {
    NEW,
    ANSWERED,
    INIT_SEEN,
    CHALLENGE_SENT,
    SAS_SENT,
    ENROLL_SENT,
    APPROVED,
    DEAD,
    ;

    val approved: Boolean get() = this == APPROVED
    val dead: Boolean get() = this == DEAD
}

object PeerTransitions {

    fun require(current: PeerPhase, allowed: Set<PeerPhase>, message: String) {
        if (current !in allowed) {
            throw IllegalStateException("WebRTC peer rejected '$message' while in $current")
        }
    }

    /** Free-form helper for logs/diagnostics. */
    fun canTransition(from: PeerPhase, to: PeerPhase): Boolean = when (to) {
        PeerPhase.NEW -> false
        PeerPhase.ANSWERED -> from == PeerPhase.NEW
        PeerPhase.INIT_SEEN -> from == PeerPhase.ANSWERED
        PeerPhase.CHALLENGE_SENT -> from == PeerPhase.INIT_SEEN
        PeerPhase.SAS_SENT -> from == PeerPhase.INIT_SEEN
        PeerPhase.ENROLL_SENT -> from == PeerPhase.SAS_SENT
        PeerPhase.APPROVED -> from == PeerPhase.CHALLENGE_SENT || from == PeerPhase.ENROLL_SENT
        PeerPhase.DEAD -> from != PeerPhase.DEAD
    }
}
