@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.sharif.sink.mesh.simulation

import com.sharif.sink.mesh.PeerRateLimiter
import com.sharif.sink.mesh.RetryPolicy
import com.sharif.sink.mesh.SendMessageOutcome
import com.sharif.sink.protocol.DeliveryState
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the real [com.sharif.sink.mesh.RoutingEngine] across simulated
 * topologies. This is the "mesh simulation layer" the product brief asks
 * for: it proves routing, retry, TTL, duplicate detection, ACK
 * propagation, and store-and-forward behavior with real code, without
 * needing physical phones.
 *
 * Long-lived collectors inside [com.sharif.sink.mesh.TransportManager] are
 * genuinely infinite (they listen for the lifetime of the app), so every
 * [SimulatedNode] here is built on `backgroundScope` — the
 * kotlinx-coroutines-test scope meant exactly for such daemon-style work,
 * auto-cancelled when the test ends, rather than the foreground test scope
 * which requires every launched job to finish.
 */
class MeshRoutingSimulationTest {

    private class FakeClock(var now: Long = 0L) {
        fun advanceBy(millis: Long) { now += millis }
        val fn: () -> Long = { now }
    }

    @Test
    fun `direct delivery between two connected nodes yields ack and DELIVERED on both sides`() = runTest {
        val network = SimulatedMeshNetwork()
        val directory = MutableIdentityDirectory()
        val alice = SimulatedNode("Alice", network, backgroundScope, directory)
        val bob = SimulatedNode("Bob", network, backgroundScope, directory)
        alice.start()
        bob.start()
        runCurrent() // let both nodes' collectors subscribe before any topology change can emit to them
        network.connect(alice.deviceId, bob.deviceId)
        runCurrent()

        val outcome = alice.routingEngine.sendMessage(bob.deviceId, "hello bob")
        assertTrue(outcome is SendMessageOutcome.Accepted)
        runCurrent()

        val onBob = bob.queue.get(outcome.messageId)
        assertEquals("hello bob", onBob?.body)
        assertEquals(DeliveryState.DELIVERED, onBob?.deliveryState)

        val onAlice = alice.queue.get(outcome.messageId)
        assertEquals(DeliveryState.DELIVERED, onAlice?.deliveryState)
    }

    @Test
    fun `four hop chain A-B-C-D delivers end to end and relays never treat it as their own message`() = runTest {
        val network = SimulatedMeshNetwork()
        val directory = MutableIdentityDirectory()
        val a = SimulatedNode("A", network, backgroundScope, directory)
        val b = SimulatedNode("B", network, backgroundScope, directory)
        val c = SimulatedNode("C", network, backgroundScope, directory)
        val d = SimulatedNode("D", network, backgroundScope, directory)
        listOf(a, b, c, d).forEach { it.start() }
        runCurrent()

        network.connect(a.deviceId, b.deviceId)
        network.connect(b.deviceId, c.deviceId)
        network.connect(c.deviceId, d.deviceId)
        runCurrent()

        val outcome = a.routingEngine.sendMessage(d.deviceId, "reach the end")
        assertTrue(outcome is SendMessageOutcome.Accepted)
        runCurrent()

        val onD = d.queue.get(outcome.messageId)
        assertEquals("reach the end", onD?.body)
        assertEquals(DeliveryState.DELIVERED, onD?.deliveryState)
        // B and C are the two relays between A and D.
        assertEquals(2, onD?.relayedThroughHopCount)

        // Relays must never record a message addressed to someone else as their own.
        assertNull(b.queue.get(outcome.messageId))
        assertNull(c.queue.get(outcome.messageId))

        val onA = a.queue.get(outcome.messageId)
        assertEquals(DeliveryState.DELIVERED, onA?.deliveryState)
    }

    @Test
    fun `duplicate copies arriving via redundant paths are not delivered twice`() = runTest {
        val network = SimulatedMeshNetwork()
        val directory = MutableIdentityDirectory()
        val a = SimulatedNode("A", network, backgroundScope, directory)
        val b = SimulatedNode("B", network, backgroundScope, directory)
        val c = SimulatedNode("C", network, backgroundScope, directory)
        val d = SimulatedNode("D", network, backgroundScope, directory)
        listOf(a, b, c, d).forEach { it.start() }
        runCurrent()

        // Two independent two-hop paths from A to D: A-B-D and A-C-D.
        network.connect(a.deviceId, b.deviceId)
        network.connect(a.deviceId, c.deviceId)
        network.connect(b.deviceId, d.deviceId)
        network.connect(c.deviceId, d.deviceId)
        runCurrent()

        var deliveredCount = 0
        backgroundScope.launch { d.routingEngine.incomingMessages.collect { deliveredCount++ } }

        val outcome = a.routingEngine.sendMessage(d.deviceId, "only once please")
        assertTrue(outcome is SendMessageOutcome.Accepted)
        runCurrent()

        assertEquals(1, deliveredCount)
    }

    @Test
    fun `message expires when ttl elapses before any route exists`() = runTest {
        val network = SimulatedMeshNetwork()
        val directory = MutableIdentityDirectory()
        val clock = FakeClock(now = 0L)
        val a = SimulatedNode("A", network, backgroundScope, directory, clock = clock.fn)
        val b = SimulatedNode("B", network, backgroundScope, directory, clock = clock.fn)
        a.start()
        b.start()
        // No connection between A and B: message can never be delivered.

        val outcome = a.routingEngine.sendMessage(b.deviceId, "will expire", ttlSeconds = 10)
        assertTrue(outcome is SendMessageOutcome.Accepted)
        runCurrent()
        assertEquals(DeliveryState.QUEUED, a.queue.get(outcome.messageId)?.deliveryState)

        clock.advanceBy(11_000)
        a.routingEngine.retrySweep()
        runCurrent()

        assertEquals(DeliveryState.EXPIRED, a.queue.get(outcome.messageId)?.deliveryState)
    }

    @Test
    fun `message exceeding max hop budget is dropped by relays and eventually marked FAILED`() = runTest {
        val network = SimulatedMeshNetwork()
        val directory = MutableIdentityDirectory()
        val clock = FakeClock(now = 0L)
        val retryPolicy = RetryPolicy(baseDelayMillis = 1_000, multiplier = 1.0, maxAttempts = 3)
        val nodes = (1..4).map { SimulatedNode("N$it", network, backgroundScope, directory, retryPolicy, clock.fn) }
        nodes.forEach { it.start() }
        runCurrent()

        // Chain of 4 nodes: reaching the last node requires 3 hops.
        network.connect(nodes[0].deviceId, nodes[1].deviceId)
        network.connect(nodes[1].deviceId, nodes[2].deviceId)
        network.connect(nodes[2].deviceId, nodes[3].deviceId)
        runCurrent()

        // maxHops = 1 means the second relay in the chain must drop the packet.
        val outcome = nodes[0].routingEngine.sendMessage(nodes[3].deviceId, "too far", maxHops = 1)
        assertTrue(outcome is SendMessageOutcome.Accepted)
        runCurrent()

        assertNull(nodes[3].queue.get(outcome.messageId))

        repeat(retryPolicy.maxAttempts) {
            clock.advanceBy(2_000)
            nodes[0].routingEngine.retrySweep()
            runCurrent()
        }

        assertEquals(DeliveryState.FAILED, nodes[0].queue.get(outcome.messageId)?.deliveryState)
    }

    @Test
    fun `store-and-forward - message queues while the only relay is gone, then delivers once it returns`() = runTest {
        val network = SimulatedMeshNetwork()
        val directory = MutableIdentityDirectory()
        val clock = FakeClock(now = 0L)
        val retryPolicy = RetryPolicy(baseDelayMillis = 1_000, multiplier = 1.0, maxAttempts = 10)
        val a = SimulatedNode("A", network, backgroundScope, directory, retryPolicy, clock.fn)
        val b = SimulatedNode("B", network, backgroundScope, directory, retryPolicy, clock.fn) // the only relay
        val c = SimulatedNode("C", network, backgroundScope, directory, retryPolicy, clock.fn)
        listOf(a, b, c).forEach { it.start() }
        runCurrent()

        // First prove B can relay for A -> C.
        network.connect(a.deviceId, b.deviceId)
        network.connect(b.deviceId, c.deviceId)
        runCurrent()

        val first = a.routingEngine.sendMessage(c.deviceId, "first message")
        assertTrue(first is SendMessageOutcome.Accepted)
        runCurrent()
        assertEquals(DeliveryState.DELIVERED, c.queue.get(first.messageId)?.deliveryState)

        // Now B disappears entirely (device turned off / walked out of range).
        network.disconnectAll(b.deviceId)
        runCurrent()

        val second = a.routingEngine.sendMessage(c.deviceId, "second message")
        assertTrue(second is SendMessageOutcome.Accepted)
        runCurrent()
        // No route exists: must not be silently dropped, must remain queued for retry.
        assertEquals(DeliveryState.QUEUED, a.queue.get(second.messageId)?.deliveryState)
        assertNull(c.queue.get(second.messageId))

        // B comes back into range of both A and C.
        network.connect(a.deviceId, b.deviceId)
        network.connect(b.deviceId, c.deviceId)
        runCurrent()

        assertEquals(DeliveryState.DELIVERED, c.queue.get(second.messageId)?.deliveryState)
        assertEquals(DeliveryState.DELIVERED, a.queue.get(second.messageId)?.deliveryState)
    }

    @Test
    fun `a flooding neighbor is rate limited and cannot deliver unlimited packets`() = runTest {
        val network = SimulatedMeshNetwork()
        val directory = MutableIdentityDirectory()
        // Victim rate-limits each neighbor to 4 packets per window: 1 automatic HELLO
        // handshake on connect, plus a budget of 3 DATA messages.
        val victim = SimulatedNode(
            "Victim",
            network,
            backgroundScope,
            directory,
            rateLimiter = PeerRateLimiter(maxPacketsPerWindow = 4, windowMillis = 60_000),
        )
        val flooder = SimulatedNode("Flooder", network, backgroundScope, directory)
        victim.start()
        flooder.start()
        runCurrent()
        network.connect(victim.deviceId, flooder.deviceId)
        runCurrent()

        var deliveredCount = 0
        backgroundScope.launch { victim.routingEngine.incomingMessages.collect { deliveredCount++ } }

        repeat(10) { i ->
            flooder.routingEngine.sendMessage(victim.deviceId, "flood #$i")
            runCurrent()
        }

        assertEquals(3, deliveredCount)
    }

    @Test
    fun `connecting nodes automatically exchange verified identities via HELLO`() = runTest {
        val network = SimulatedMeshNetwork()
        val directory = MutableIdentityDirectory()
        val alice = SimulatedNode("Alice", network, backgroundScope, directory)
        val bob = SimulatedNode("Bob", network, backgroundScope, directory)
        alice.start()
        bob.start()

        val aliceSawBob = mutableListOf<com.sharif.sink.protocol.DeviceIdentity>()
        val bobSawAlice = mutableListOf<com.sharif.sink.protocol.DeviceIdentity>()
        backgroundScope.launch { alice.routingEngine.discoveredIdentities.collect { aliceSawBob += it } }
        backgroundScope.launch { bob.routingEngine.discoveredIdentities.collect { bobSawAlice += it } }
        runCurrent() // let every collector above subscribe before the connect below can emit to them

        network.connect(alice.deviceId, bob.deviceId)
        runCurrent()

        assertEquals(1, aliceSawBob.size)
        assertEquals(bob.deviceId, aliceSawBob.first().deviceId)

        assertEquals(1, bobSawAlice.size)
        assertEquals(alice.deviceId, bobSawAlice.first().deviceId)
    }
}
