package com.sharif.sink.protocol

/** Current wire protocol version. Bump on any incompatible schema change. */
const val PROTOCOL_VERSION: Int = 1

/** Sink refuses to process packets from a protocol version it doesn't understand. */
class UnsupportedProtocolVersionException(val version: Int) :
    Exception("Unsupported Sink protocol version: $version")

enum class PacketType {
    /** Carries an end-to-end encrypted message payload. */
    DATA,

    /** Acknowledges receipt of a [DATA] packet by its final recipient. */
    ACK,

    /** Lightweight presence/capability announcement between directly connected peers. */
    HELLO,
}

/**
 * Encryption envelope for a [SinkPacket] payload. Relay nodes only ever see
 * this metadata and the ciphertext — never plaintext.
 */
data class EncryptionMetadata(
    val algorithm: String,
    val senderEphemeralPublicKey: ByteArray,
    val nonce: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptionMetadata) return false
        return algorithm == other.algorithm &&
            senderEphemeralPublicKey.contentEquals(other.senderEphemeralPublicKey) &&
            nonce.contentEquals(other.nonce)
    }

    override fun hashCode(): Int {
        var result = algorithm.hashCode()
        result = 31 * result + senderEphemeralPublicKey.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        return result
    }
}

/**
 * The wire-level unit routed through the mesh. Intermediate/relay nodes
 * read and mutate only [ttl]/[hopCount] and forwarding bookkeeping — never
 * [ciphertext].
 *
 * Field naming spells out its conceptual packet (protocolVersion,
 * packetType, messageId, senderId, destinationId, ttl, hopCount, timestamp,
 * payload, authenticationMetadata) explicitly, specialized for a two-party
 * store-and-forward design.
 */
data class SinkPacket(
    val protocolVersion: Int = PROTOCOL_VERSION,
    val packetType: PacketType,
    val messageId: MessageId,
    val senderId: DeviceId,
    val destinationId: DeviceId,
    val conversationId: ConversationId,
    val createdAtEpochMillis: Long,
    val ttlSeconds: Int,
    val hopCount: Int,
    val maxHops: Int,
    val ciphertext: ByteArray,
    val encryptionMetadata: EncryptionMetadata,
    /** Signature over the packet's canonical bytes, by the original sender's identity key. */
    val senderSignature: ByteArray,
    /** Only set when [packetType] == [PacketType.ACK]: the id being acknowledged. */
    val acknowledgedMessageId: MessageId? = null,
) {
    fun isExpired(nowEpochMillis: Long): Boolean {
        val ageSeconds = (nowEpochMillis - createdAtEpochMillis) / 1000
        return ageSeconds >= ttlSeconds
    }

    fun hopLimitReached(): Boolean = hopCount >= maxHops

    fun withNextHop(): SinkPacket = copy(hopCount = hopCount + 1)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SinkPacket) return false
        return protocolVersion == other.protocolVersion &&
            packetType == other.packetType &&
            messageId == other.messageId &&
            senderId == other.senderId &&
            destinationId == other.destinationId &&
            conversationId == other.conversationId &&
            createdAtEpochMillis == other.createdAtEpochMillis &&
            ttlSeconds == other.ttlSeconds &&
            hopCount == other.hopCount &&
            maxHops == other.maxHops &&
            ciphertext.contentEquals(other.ciphertext) &&
            encryptionMetadata == other.encryptionMetadata &&
            senderSignature.contentEquals(other.senderSignature) &&
            acknowledgedMessageId == other.acknowledgedMessageId
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + packetType.hashCode()
        result = 31 * result + hopCount
        return result
    }

    companion object {
        /** Default lifetime for a routed message before it's considered undeliverable. */
        const val DEFAULT_TTL_SECONDS: Int = 60 * 60 * 24 // 24 hours

        /** Default maximum number of relay hops before a packet is dropped, not forwarded. */
        const val DEFAULT_MAX_HOPS: Int = 6
    }
}
