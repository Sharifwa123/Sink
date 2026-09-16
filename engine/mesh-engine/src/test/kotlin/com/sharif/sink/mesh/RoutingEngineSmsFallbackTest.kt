@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.sharif.sink.mesh

import com.sharif.sink.crypto.Fingerprint
import com.sharif.sink.crypto.SinkKeyPairs
import com.sharif.sink.protocol.DeliveryState
import com.sharif.sink.protocol.DeviceId
import com.sharif.sink.protocol.SinkPacket
import com.sharif.sink.protocol.TransportKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A transport whose [connectedPeers] is always empty — modeling SmsTransport, which has
 * no persistent "connection" concept — but that still honestly accepts or rejects an
 * explicit direct [send], exactly like the real SMS transport does.
 */
private class FakeAlwaysDeliversTransport(override val kind: TransportKind) : CommunicationTransport {
    override val capabilities = TransportCapabilities.TEXT_ONLY_SMS
    override val connectedPeers: StateFlow<Set<DeviceId>> = MutableStateFlow(emptySet())
    override val incomingPackets: Flow<IncomingPacket> = MutableSharedFlow()
    var shouldSucceed = true

    override suspend fun start() = Unit
    override suspend fun stop() = Unit
    override suspend fun send(peer: DeviceId, packet: SinkPacket): TransportSendResult =
        if (shouldSucceed) TransportSendResult.Delivered else TransportSendResult.Failed("no signal")
}

/** An always-unreachable mesh transport, so automatic delivery/retry can never succeed. */
private class FakeUnreachableTransport : CommunicationTransport {
    override val kind = TransportKind.LOCAL_MESH
    override val capabilities = TransportCapabilities.LOCAL_MESH_TEXT
    override val connectedPeers: StateFlow<Set<DeviceId>> = MutableStateFlow(emptySet())
    override val incomingPackets: Flow<IncomingPacket> = MutableSharedFlow()
    override suspend fun start() = Unit
    override suspend fun stop() = Unit
    override suspend fun send(peer: DeviceId, packet: SinkPacket): TransportSendResult =
        TransportSendResult.Failed("unreachable")
}

class RoutingEngineSmsFallbackTest {

    @Test
    fun `a failed message can still be explicitly sent via SMS`() = runTest {
        val signingKeyPair = SinkKeyPairs.generate()
        val agreementKeyPair = SinkKeyPairs.generate()
        val localDeviceId = DeviceId(Fingerprint.of(signingKeyPair.public))
        val localIdentity = LocalIdentity(localDeviceId, signingKeyPair, agreementKeyPair)

        val recipientKeys = SinkKeyPairs.generate()
        val recipientId = DeviceId(Fingerprint.of(recipientKeys.public))
        val directory = object : IdentityDirectory {
            override suspend fun signingPublicKeyOf(deviceId: DeviceId) = null
            override suspend fun agreementPublicKeyOf(deviceId: DeviceId) =
                if (deviceId == recipientId) recipientKeys.public else null
        }

        val mesh = FakeUnreachableTransport()
        val sms = FakeAlwaysDeliversTransport(TransportKind.SMS)
        val transportManager = TransportManager(transports = listOf(mesh, sms), scope = backgroundScope)
        val queue = InMemoryMessageQueue()
        val retryPolicy = RetryPolicy(baseDelayMillis = 0, multiplier = 1.0, maxAttempts = 2)

        val routingEngine = RoutingEngine(
            localIdentity = localIdentity,
            transportManager = transportManager,
            identityDirectory = directory,
            messageQueue = queue,
            scope = backgroundScope,
            retryPolicy = retryPolicy,
        )

        val outcome = routingEngine.sendMessage(recipientId, "urgent")
        check(outcome is SendMessageOutcome.Accepted)

        // Exhaust automatic retries against the unreachable mesh transport.
        repeat(retryPolicy.maxAttempts) { routingEngine.retrySweep() }
        assertEquals(DeliveryState.FAILED, queue.get(outcome.messageId)?.deliveryState)

        // The user taps "Send by SMS" on the failed message.
        val smsOutcome = routingEngine.sendViaSpecificTransport(outcome.messageId, TransportKind.SMS)
        assertTrue(smsOutcome is SendMessageOutcome.Accepted)
        assertEquals(DeliveryState.SENT_TO_PEER, queue.get(outcome.messageId)?.deliveryState)
        assertEquals(TransportKind.SMS, queue.get(outcome.messageId)?.deliveredViaTransport)
    }

    @Test
    fun `sms fallback fails cleanly when the sms transport itself fails`() = runTest {
        val signingKeyPair = SinkKeyPairs.generate()
        val agreementKeyPair = SinkKeyPairs.generate()
        val localDeviceId = DeviceId(Fingerprint.of(signingKeyPair.public))
        val localIdentity = LocalIdentity(localDeviceId, signingKeyPair, agreementKeyPair)

        val recipientKeys = SinkKeyPairs.generate()
        val recipientId = DeviceId(Fingerprint.of(recipientKeys.public))
        val directory = object : IdentityDirectory {
            override suspend fun signingPublicKeyOf(deviceId: DeviceId) = null
            override suspend fun agreementPublicKeyOf(deviceId: DeviceId) =
                if (deviceId == recipientId) recipientKeys.public else null
        }

        val sms = FakeAlwaysDeliversTransport(TransportKind.SMS).apply { shouldSucceed = false }
        val transportManager = TransportManager(transports = listOf(sms), scope = backgroundScope)
        val queue = InMemoryMessageQueue()

        val routingEngine = RoutingEngine(
            localIdentity = localIdentity,
            transportManager = transportManager,
            identityDirectory = directory,
            messageQueue = queue,
            scope = backgroundScope,
        )

        val outcome = routingEngine.sendMessage(recipientId, "urgent")
        check(outcome is SendMessageOutcome.Accepted)

        val smsOutcome = routingEngine.sendViaSpecificTransport(outcome.messageId, TransportKind.SMS)
        assertTrue(smsOutcome is SendMessageOutcome.Rejected)
    }

    @Test
    fun `sms fallback rejects an unknown message id`() = runTest {
        val signingKeyPair = SinkKeyPairs.generate()
        val agreementKeyPair = SinkKeyPairs.generate()
        val localDeviceId = DeviceId(Fingerprint.of(signingKeyPair.public))
        val localIdentity = LocalIdentity(localDeviceId, signingKeyPair, agreementKeyPair)

        val directory = object : IdentityDirectory {
            override suspend fun signingPublicKeyOf(deviceId: DeviceId) = null
            override suspend fun agreementPublicKeyOf(deviceId: DeviceId) = null
        }
        val transportManager = TransportManager(transports = emptyList(), scope = backgroundScope)
        val routingEngine = RoutingEngine(
            localIdentity = localIdentity,
            transportManager = transportManager,
            identityDirectory = directory,
            messageQueue = InMemoryMessageQueue(),
            scope = backgroundScope,
        )

        val result = routingEngine.sendViaSpecificTransport(
            com.sharif.sink.protocol.MessageId.new(),
            TransportKind.SMS,
        )
        assertTrue(result is SendMessageOutcome.Rejected)
    }
}
