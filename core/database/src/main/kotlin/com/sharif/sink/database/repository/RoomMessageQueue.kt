package com.sharif.sink.database.repository

import com.sharif.sink.database.dao.ContactDao
import com.sharif.sink.database.dao.ConversationDao
import com.sharif.sink.database.dao.MessageDao
import com.sharif.sink.database.entity.ConversationEntity
import com.sharif.sink.mesh.MessageQueue
import com.sharif.sink.protocol.DeliveryState
import com.sharif.sink.protocol.DeviceId
import com.sharif.sink.protocol.Message
import com.sharif.sink.protocol.MessageId
import com.sharif.sink.protocol.TransportKind

/**
 * Room-backed [MessageQueue]: this is what makes the outgoing queue durable
 * across process death and reboot, so a killed app process never loses a
 * queued message.
 */
class RoomMessageQueue(
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val contactDao: ContactDao,
    private val localDeviceId: () -> DeviceId,
) : MessageQueue {

    override suspend fun enqueue(message: Message) {
        val isOutgoing = message.senderId == localDeviceId()
        messageDao.upsert(message.toEntity(isOutgoing))

        val conversation = conversationDao.get(message.conversationId.value)
        val peerId = if (isOutgoing) message.recipientId else message.senderId
        if (conversation == null) {
            // The peer's real name is usually already known by now — HELLO resolves on
            // connect, before any message flows — but fall back to the raw id rather than
            // block sending on it; PeerIdentityPersister corrects this later if needed.
            val peerDisplayName = contactDao.get(peerId.value)?.displayName?.takeIf { it.isNotBlank() } ?: peerId.value
            conversationDao.upsert(
                ConversationEntity(
                    conversationId = message.conversationId.value,
                    peerDeviceId = peerId.value,
                    peerDisplayName = peerDisplayName,
                    lastMessagePreview = message.body,
                    lastMessageAtEpochMillis = message.createdAtEpochMillis,
                    unreadCount = if (isOutgoing) 0 else 1,
                ),
            )
        } else {
            conversationDao.updateLastMessage(message.conversationId.value, message.body, message.createdAtEpochMillis)
            if (!isOutgoing) conversationDao.incrementUnread(message.conversationId.value)
        }
    }

    override suspend fun get(id: MessageId): Message? = messageDao.get(id.value)?.toDomain()

    override suspend fun updateState(id: MessageId, state: DeliveryState, transport: TransportKind?, relayedHopCount: Int?) {
        messageDao.updateState(id.value, state.name, transport?.name, relayedHopCount)
    }

    override suspend fun pendingForRetry(): List<Message> = messageDao.pendingForRetry().map { it.toDomain() }

    override suspend fun all(): List<Message> = messageDao.all().map { it.toDomain() }
}
