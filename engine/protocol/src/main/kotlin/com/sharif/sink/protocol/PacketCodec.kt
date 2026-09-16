package com.sharif.sink.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Manual binary wire format for [SinkPacket] — deliberately not JSON/protobuf:
 * packets travel over small-payload transports (BLE-backed Nearby links,
 * and potentially SMS-sized chunks), so a compact, dependency-free format
 * keeps the framing predictable and easy to reason about across
 * transports. Every field is length-prefixed; unknown future fields are
 * out of scope for this protocol version (see [PROTOCOL_VERSION]).
 */
object PacketCodec {

    fun encode(packet: SinkPacket): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(packet.protocolVersion)
            d.writeUTF(packet.packetType.name)
            d.writeUTF(packet.messageId.value)
            d.writeUTF(packet.senderId.value)
            d.writeUTF(packet.destinationId.value)
            d.writeUTF(packet.conversationId.value)
            d.writeLong(packet.createdAtEpochMillis)
            d.writeInt(packet.ttlSeconds)
            d.writeInt(packet.hopCount)
            d.writeInt(packet.maxHops)
            d.writeByteArray(packet.ciphertext)
            d.writeUTF(packet.encryptionMetadata.algorithm)
            d.writeByteArray(packet.encryptionMetadata.senderEphemeralPublicKey)
            d.writeByteArray(packet.encryptionMetadata.nonce)
            d.writeByteArray(packet.senderSignature)
            d.writeBoolean(packet.acknowledgedMessageId != null)
            packet.acknowledgedMessageId?.let { d.writeUTF(it.value) }
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): SinkPacket {
        DataInputStream(ByteArrayInputStream(bytes)).use { d ->
            val protocolVersion = d.readInt()
            if (protocolVersion != PROTOCOL_VERSION) throw UnsupportedProtocolVersionException(protocolVersion)

            val packetType = PacketType.valueOf(d.readUTF())
            val messageId = MessageId(d.readUTF())
            val senderId = DeviceId(d.readUTF())
            val destinationId = DeviceId(d.readUTF())
            val conversationId = ConversationId(d.readUTF())
            val createdAt = d.readLong()
            val ttl = d.readInt()
            val hopCount = d.readInt()
            val maxHops = d.readInt()
            val ciphertext = d.readLengthPrefixedBytes()
            val algorithm = d.readUTF()
            val ephemeralKey = d.readLengthPrefixedBytes()
            val nonce = d.readLengthPrefixedBytes()
            val signature = d.readLengthPrefixedBytes()
            val hasAck = d.readBoolean()
            val ackId = if (hasAck) MessageId(d.readUTF()) else null

            return SinkPacket(
                protocolVersion = protocolVersion,
                packetType = packetType,
                messageId = messageId,
                senderId = senderId,
                destinationId = destinationId,
                conversationId = conversationId,
                createdAtEpochMillis = createdAt,
                ttlSeconds = ttl,
                hopCount = hopCount,
                maxHops = maxHops,
                ciphertext = ciphertext,
                encryptionMetadata = EncryptionMetadata(algorithm, ephemeralKey, nonce),
                senderSignature = signature,
                acknowledgedMessageId = ackId,
            )
        }
    }

    // DataOutputStream.writeBytes(String) writes raw chars with no length prefix and no encoding
    // safety, so byte payloads use a small hand-rolled length-prefixed helper instead.
    private fun DataOutputStream.writeByteArray(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readLengthPrefixedBytes(): ByteArray {
        val length = readInt()
        val buffer = ByteArray(length)
        readFully(buffer)
        return buffer
    }
}
