package com.sharif.sink.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local record of a message, outgoing or incoming. [messageId] is the
 * mesh-wide UUID (never a local auto-increment id) so it stays meaningful
 * across devices and survives being looked up by ACKs from anywhere in
 * the mesh.
 */
@Entity(
    tableName = "messages",
    indices = [Index("conversationId"), Index("deliveryState")],
)
data class MessageEntity(
    @PrimaryKey val messageId: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val body: String,
    val createdAtEpochMillis: Long,
    val ttlSeconds: Int,
    val maxHops: Int,
    val hopCount: Int,
    val deliveryState: String,
    val deliveredViaTransport: String?,
    val relayedThroughHopCount: Int?,
    val isOutgoing: Boolean,
)
