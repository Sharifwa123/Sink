package com.sharif.sink.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SinkPacketTest {

    private fun samplePacket(
        createdAt: Long = 0L,
        ttlSeconds: Int = 100,
        hopCount: Int = 0,
        maxHops: Int = 3,
    ) = SinkPacket(
        packetType = PacketType.DATA,
        messageId = MessageId.new(),
        senderId = DeviceId("device-a"),
        destinationId = DeviceId("device-d"),
        conversationId = ConversationId("dm:device-a:device-d"),
        createdAtEpochMillis = createdAt,
        ttlSeconds = ttlSeconds,
        hopCount = hopCount,
        maxHops = maxHops,
        ciphertext = byteArrayOf(1, 2, 3),
        encryptionMetadata = EncryptionMetadata(
            algorithm = "AES-256-GCM",
            senderEphemeralPublicKey = byteArrayOf(9, 9),
            nonce = byteArrayOf(1),
        ),
        senderSignature = byteArrayOf(4, 5, 6),
    )

    @Test
    fun `packet is not expired before ttl elapses`() {
        val packet = samplePacket(createdAt = 0L, ttlSeconds = 100)
        assertFalse(packet.isExpired(nowEpochMillis = 50_000))
    }

    @Test
    fun `packet is expired once ttl elapses`() {
        val packet = samplePacket(createdAt = 0L, ttlSeconds = 100)
        assertTrue(packet.isExpired(nowEpochMillis = 100_001))
    }

    @Test
    fun `hop limit reached when hopCount equals maxHops`() {
        val packet = samplePacket(hopCount = 3, maxHops = 3)
        assertTrue(packet.hopLimitReached())
    }

    @Test
    fun `hop limit not reached below maxHops`() {
        val packet = samplePacket(hopCount = 2, maxHops = 3)
        assertFalse(packet.hopLimitReached())
    }

    @Test
    fun `withNextHop increments hop count only`() {
        val packet = samplePacket(hopCount = 1)
        val next = packet.withNextHop()
        assertEquals(2, next.hopCount)
        assertEquals(packet.messageId, next.messageId)
    }

    @Test
    fun `conversationId for direct message is order independent`() {
        val a = DeviceId("aaa")
        val b = DeviceId("bbb")
        assertEquals(ConversationId.forDirectMessage(a, b), ConversationId.forDirectMessage(b, a))
    }
}
