package com.sharif.sink.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PacketCodecTest {

    private fun samplePacket(ackId: MessageId? = null) = SinkPacket(
        packetType = if (ackId != null) PacketType.ACK else PacketType.DATA,
        messageId = MessageId.new(),
        senderId = DeviceId("device-a"),
        destinationId = DeviceId("device-d"),
        conversationId = ConversationId("dm:device-a:device-d"),
        createdAtEpochMillis = 1_700_000_000_000L,
        ttlSeconds = 3600,
        hopCount = 2,
        maxHops = 6,
        ciphertext = byteArrayOf(1, 2, 3, 4, 5),
        encryptionMetadata = EncryptionMetadata(
            algorithm = "ECDH-P256+HKDF-SHA256+AES-256-GCM",
            senderEphemeralPublicKey = byteArrayOf(9, 8, 7),
            nonce = byteArrayOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
        ),
        senderSignature = byteArrayOf(4, 5, 6, 7),
        acknowledgedMessageId = ackId,
    )

    @Test
    fun `data packet survives encode-decode round trip`() {
        val original = samplePacket()
        val decoded = PacketCodec.decode(PacketCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `ack packet with acknowledgedMessageId survives round trip`() {
        val original = samplePacket(ackId = MessageId.new())
        val decoded = PacketCodec.decode(PacketCodec.encode(original))
        assertEquals(original, decoded)
        assertEquals(original.acknowledgedMessageId, decoded.acknowledgedMessageId)
    }

    @Test
    fun `empty byte fields round trip correctly`() {
        val original = samplePacket().copy(
            ciphertext = ByteArray(0),
            senderSignature = ByteArray(0),
        )
        val decoded = PacketCodec.decode(PacketCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `unsupported protocol version fails to decode`() {
        val bytes = PacketCodec.encode(samplePacket())
        // Corrupt the first 4 bytes (protocolVersion, big-endian) to an unknown version.
        bytes[0] = 0
        bytes[1] = 0
        bytes[2] = 0
        bytes[3] = 99

        assertFailsWith<UnsupportedProtocolVersionException> {
            PacketCodec.decode(bytes)
        }
    }
}
