package com.sharif.sink.database.repository

import com.sharif.sink.database.entity.MessageEntity
import com.sharif.sink.protocol.ConversationId
import com.sharif.sink.protocol.DeliveryState
import com.sharif.sink.protocol.DeviceId
import com.sharif.sink.protocol.Message
import com.sharif.sink.protocol.MessageId
import com.sharif.sink.protocol.TransportKind

fun Message.toEntity(isOutgoing: Boolean): MessageEntity = MessageEntity(
    messageId = messageId.value,
    conversationId = conversationId.value,
    senderId = senderId.value,
    recipientId = recipientId.value,
    body = body,
    createdAtEpochMillis = createdAtEpochMillis,
    ttlSeconds = ttlSeconds,
    maxHops = maxHops,
    hopCount = hopCount,
    deliveryState = deliveryState.name,
    deliveredViaTransport = deliveredViaTransport?.name,
    relayedThroughHopCount = relayedThroughHopCount,
    isOutgoing = isOutgoing,
)

fun MessageEntity.toDomain(): Message = Message(
    messageId = MessageId(messageId),
    conversationId = ConversationId(conversationId),
    senderId = DeviceId(senderId),
    recipientId = DeviceId(recipientId),
    body = body,
    createdAtEpochMillis = createdAtEpochMillis,
    ttlSeconds = ttlSeconds,
    maxHops = maxHops,
    hopCount = hopCount,
    deliveryState = DeliveryState.valueOf(deliveryState),
    deliveredViaTransport = deliveredViaTransport?.let { TransportKind.valueOf(it) },
    relayedThroughHopCount = relayedThroughHopCount,
)
