package com.sharif.sink.mesh

import com.sharif.sink.protocol.DeviceId

/**
 * Fixed-window rate limit per directly-connected peer. Every packet a
 * device hands us — however many hops it claims to have traveled — is
 * attributed to the *neighbor that handed it to us*, not to whatever
 * sender id it claims, since that claim isn't verified until much later
 * (signature check happens only for packets addressed to us). This is
 * what stops a single malicious or malfunctioning neighbor from cheaply
 * flooding a node with packets before any crypto is even attempted.
 */
class PeerRateLimiter(
    private val maxPacketsPerWindow: Int = 120,
    private val windowMillis: Long = 60_000,
) {
    private class Window(var windowStart: Long, var count: Int)

    private val perPeer = HashMap<DeviceId, Window>()

    @Synchronized
    fun allow(peer: DeviceId, nowEpochMillis: Long): Boolean {
        val window = perPeer.getOrPut(peer) { Window(nowEpochMillis, 0) }
        if (nowEpochMillis - window.windowStart >= windowMillis) {
            window.windowStart = nowEpochMillis
            window.count = 0
        }
        window.count++
        return window.count <= maxPacketsPerWindow
    }
}
