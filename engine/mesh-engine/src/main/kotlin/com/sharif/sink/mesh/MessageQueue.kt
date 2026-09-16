package com.sharif.sink.mesh

import com.sharif.sink.protocol.DeliveryState
import com.sharif.sink.protocol.Message
import com.sharif.sink.protocol.MessageId
import com.sharif.sink.protocol.TransportKind
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Durable outgoing/incoming message record. The real Android app backs this
 * with Room (core:database) so the queue survives process death and
 * reboot; this module only defines the contract and a simple in-memory
 * implementation used for engine-level tests and the mesh simulator.
 */
interface MessageQueue {
    suspend fun enqueue(message: Message)
    suspend fun get(id: MessageId): Message?
    suspend fun updateState(
        id: MessageId,
        state: DeliveryState,
        transport: TransportKind? = null,
        relayedHopCount: Int? = null,
    )

    suspend fun pendingForRetry(): List<Message>
    suspend fun all(): List<Message>
}

class InMemoryMessageQueue : MessageQueue {
    private val mutex = Mutex()
    private val messages = LinkedHashMap<MessageId, Message>()

    override suspend fun enqueue(message: Message) = mutex.withLock {
        messages[message.messageId] = message
    }

    override suspend fun get(id: MessageId): Message? = mutex.withLock { messages[id] }

    override suspend fun updateState(
        id: MessageId,
        state: DeliveryState,
        transport: TransportKind?,
        relayedHopCount: Int?,
    ) = mutex.withLock {
        val existing = messages[id] ?: return@withLock
        messages[id] = existing.copy(
            deliveryState = state,
            deliveredViaTransport = transport ?: existing.deliveredViaTransport,
            relayedThroughHopCount = relayedHopCount ?: existing.relayedThroughHopCount,
        )
    }

    override suspend fun pendingForRetry(): List<Message> = mutex.withLock {
        messages.values.filter { it.deliveryState.isOutgoingInFlight }
    }

    override suspend fun all(): List<Message> = mutex.withLock { messages.values.toList() }
}
