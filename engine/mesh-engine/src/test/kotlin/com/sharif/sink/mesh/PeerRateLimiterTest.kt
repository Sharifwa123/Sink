package com.sharif.sink.mesh

import com.sharif.sink.protocol.DeviceId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeerRateLimiterTest {

    @Test
    fun `allows packets up to the window limit`() {
        val limiter = PeerRateLimiter(maxPacketsPerWindow = 3, windowMillis = 10_000)
        val peer = DeviceId("peer")
        assertTrue(limiter.allow(peer, 0))
        assertTrue(limiter.allow(peer, 0))
        assertTrue(limiter.allow(peer, 0))
    }

    @Test
    fun `rejects packets beyond the window limit`() {
        val limiter = PeerRateLimiter(maxPacketsPerWindow = 3, windowMillis = 10_000)
        val peer = DeviceId("peer")
        repeat(3) { limiter.allow(peer, 0) }
        assertFalse(limiter.allow(peer, 0))
        assertFalse(limiter.allow(peer, 100))
    }

    @Test
    fun `resets after the window elapses`() {
        val limiter = PeerRateLimiter(maxPacketsPerWindow = 2, windowMillis = 1_000)
        val peer = DeviceId("peer")
        assertTrue(limiter.allow(peer, 0))
        assertTrue(limiter.allow(peer, 0))
        assertFalse(limiter.allow(peer, 500))

        assertTrue(limiter.allow(peer, 1_500))
    }

    @Test
    fun `peers are tracked independently`() {
        val limiter = PeerRateLimiter(maxPacketsPerWindow = 1, windowMillis = 10_000)
        val a = DeviceId("a")
        val b = DeviceId("b")
        assertTrue(limiter.allow(a, 0))
        assertFalse(limiter.allow(a, 0))
        assertTrue(limiter.allow(b, 0))
    }
}
