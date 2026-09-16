package com.sharif.sink.mesh

import com.sharif.sink.protocol.SinkPacket

/**
 * Deterministic byte encoding of the fields a packet's sender-signature
 * covers. Deliberately excludes [SinkPacket.hopCount]: relays legitimately
 * increment it in transit, and the signature must still verify unchanged
 * at the final recipient.
 */
object PacketSigning {
    fun canonicalBytes(packet: SinkPacket): ByteArray {
        val header = buildString {
            append(packet.protocolVersion).append('|')
            append(packet.packetType).append('|')
            append(packet.messageId.value).append('|')
            append(packet.senderId.value).append('|')
            append(packet.destinationId.value).append('|')
            append(packet.conversationId.value).append('|')
            append(packet.createdAtEpochMillis).append('|')
            append(packet.ttlSeconds).append('|')
            append(packet.maxHops).append('|')
            append(packet.acknowledgedMessageId?.value ?: "-")
        }.toByteArray(Charsets.UTF_8)

        return header +
            packet.ciphertext +
            packet.encryptionMetadata.senderEphemeralPublicKey +
            packet.encryptionMetadata.nonce
    }
}
