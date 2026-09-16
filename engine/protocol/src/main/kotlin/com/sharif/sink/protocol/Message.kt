package com.sharif.sink.protocol

/**
 * Application-level (decrypted) representation of a message, as stored
 * locally and shown in the UI. Distinct from [SinkPacket], which is the
 * encrypted wire form relays actually see.
 */
data class Message(
    val messageId: MessageId,
    val conversationId: ConversationId,
    val senderId: DeviceId,
    val recipientId: DeviceId,
    val body: String,
    val createdAtEpochMillis: Long,
    val ttlSeconds: Int = SinkPacket.DEFAULT_TTL_SECONDS,
    val maxHops: Int = SinkPacket.DEFAULT_MAX_HOPS,
    val hopCount: Int = 0,
    val deliveryState: DeliveryState = DeliveryState.QUEUED,
    val deliveredViaTransport: TransportKind? = null,
    val relayedThroughHopCount: Int? = null,
)

/**
 * Transport that actually carried a message for the leg being described.
 * Used only for UI transparency ("Delivered via nearby mesh", "Sent by SMS") —
 * never invented when unknown.
 */
enum class TransportKind {
    LOCAL_MESH,
    NEARBY_DIRECT,
    INTERNET,
    SMS,
}
