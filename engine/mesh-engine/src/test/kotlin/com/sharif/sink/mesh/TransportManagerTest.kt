@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.sharif.sink.mesh

import com.sharif.sink.protocol.DeviceId
import com.sharif.sink.protocol.SinkPacket
import com.sharif.sink.protocol.TransportKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Minimal fake used only to test [TransportManager] ranking/aggregation logic in isolation. */
private class FakeTransport(
    override val kind: TransportKind,
    connected: Set<DeviceId> = emptySet(),
) : CommunicationTransport {
    override val capabilities = TransportCapabilities.LOCAL_MESH_TEXT
    override val connectedPeers: MutableStateFlow<Set<DeviceId>> = MutableStateFlow(connected)
    override val incomingPackets: Flow<IncomingPacket> = emptyFlow()

    val sentTo = mutableListOf<DeviceId>()

    override suspend fun start() = Unit
    override suspend fun stop() = Unit
    override suspend fun send(peer: DeviceId, packet: SinkPacket): TransportSendResult {
        sentTo += peer
        return if (peer in connectedPeers.value) TransportSendResult.Delivered else TransportSendResult.Failed("unreachable")
    }
}

class TransportManagerTest {

    @Test
    fun `higher priority transport is preferred when peer reachable via both`() = runTest {
        val peer = DeviceId("peer-1")
        val mesh = FakeTransport(TransportKind.LOCAL_MESH, setOf(peer))
        val internet = FakeTransport(TransportKind.INTERNET, setOf(peer))

        val manager = TransportManager(
            transports = listOf(internet, mesh),
            priority = listOf(TransportKind.LOCAL_MESH, TransportKind.INTERNET),
            scope = this,
        )

        val reachable = manager.reachablePeers()
        assertEquals(TransportKind.LOCAL_MESH, reachable[peer]?.kind)
    }

    @Test
    fun `sms is never included in automatic broadcast`() = runTest {
        val peer = DeviceId("peer-1")
        val sms = FakeTransport(TransportKind.SMS, setOf(peer))

        val manager = TransportManager(transports = listOf(sms), scope = this)
        assertTrue(manager.reachablePeers().isEmpty())
    }

    @Test
    fun `unreachable peer yields failed send result`() = runTest {
        val mesh = FakeTransport(TransportKind.LOCAL_MESH, emptySet())
        val manager = TransportManager(transports = listOf(mesh), scope = this)

        val result = manager.sendToPeer(DeviceId("nobody"), samplePacket())
        assertTrue(result is TransportSendResult.Failed)
    }

    @Test
    fun `connect and disconnect listeners fire as peers appear and disappear`() = runTest {
        val peer = DeviceId("peer-1")
        val mesh = FakeTransport(TransportKind.LOCAL_MESH, emptySet())
        val manager = TransportManager(transports = listOf(mesh), scope = backgroundScope)

        val connected = mutableListOf<DeviceId>()
        val disconnected = mutableListOf<DeviceId>()
        manager.registerPeerConnectedListener { connected += it }
        manager.registerPeerDisconnectedListener { disconnected += it }

        manager.start()
        runCurrent()

        mesh.connectedPeers.value = setOf(peer)
        runCurrent()
        assertEquals(listOf(peer), connected)
        assertTrue(disconnected.isEmpty())

        mesh.connectedPeers.value = emptySet()
        runCurrent()
        assertEquals(listOf(peer), disconnected)
    }

    private fun samplePacket(): SinkPacket = SinkPacket(
        packetType = com.sharif.sink.protocol.PacketType.DATA,
        messageId = com.sharif.sink.protocol.MessageId.new(),
        senderId = DeviceId("a"),
        destinationId = DeviceId("b"),
        conversationId = com.sharif.sink.protocol.ConversationId("dm:a:b"),
        createdAtEpochMillis = 0,
        ttlSeconds = 60,
        hopCount = 0,
        maxHops = 3,
        ciphertext = byteArrayOf(1),
        encryptionMetadata = com.sharif.sink.protocol.EncryptionMetadata("NONE", byteArrayOf(), byteArrayOf()),
        senderSignature = byteArrayOf(),
    )
}
