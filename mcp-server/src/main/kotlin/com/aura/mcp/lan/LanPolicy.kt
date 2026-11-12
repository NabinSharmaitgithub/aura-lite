package com.aura.mcp.lan

import java.net.Inet4Address
import java.net.InetAddress

object LanPolicy {
    const val DEFAULT_PORT = 47821
    val PORTS: IntRange = DEFAULT_PORT..(DEFAULT_PORT + 4)

    fun isLanIpv4(address: String): Boolean {
        val parts = address.split('.')
        if (parts.size != 4) return false
        val b = IntArray(4)
        for (i in 0..3) {
            val p = parts[i]
            if (p.isEmpty() || p.length > 3 || !p.all(Char::isDigit)) return false
            b[i] = p.toInt()
            if (b[i] > 255) return false
        }
        return b[0] == 10 ||
            (b[0] == 172 && b[1] in 16..31) ||
            (b[0] == 192 && b[1] == 168) ||
            (b[0] == 169 && b[1] == 254) ||
            (b[0] == 100 && b[1] in 64..127)
    }

    fun isLanPeer(peer: InetAddress): Boolean =
        peer.isLoopbackAddress || (peer is Inet4Address && isLanIpv4(peer.hostAddress ?: ""))

    fun filterSdp(sdp: String): String =
        sdp.split("\r\n", "\n")
            .filter { line -> !line.startsWith("a=candidate:") || isLanCandidate(line) }
            .joinToString("\r\n")

    fun lanCandidateCount(sdp: String): Int =
        sdp.split("\r\n", "\n").count { it.startsWith("a=candidate:") && isLanCandidate(it) }

    internal fun isLanCandidate(line: String): Boolean {
        val f = line.removePrefix("a=candidate:").trim().split(Regex("\\s+"))
        if (f.size < 8 || f[6] != "typ") return false
        return f[7] == "host" && isLanIpv4(f[4])
    }
}
