package com.sharif.sink.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sharif.sink.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    // REPLACE keyed by messageId is intentional: a message can arrive more than once as
    // routing/ACK state updates come in, and messageId (not any local id) is the identity.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE messageId = :messageId")
    suspend fun get(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAtEpochMillis ASC")
    fun observeConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query(
        "SELECT * FROM messages WHERE deliveryState IN ('QUEUED', 'SENDING', 'SENT_TO_PEER', 'RELAYING')",
    )
    suspend fun pendingForRetry(): List<MessageEntity>

    @Query("SELECT * FROM messages")
    suspend fun all(): List<MessageEntity>

    @Query(
        "UPDATE messages SET deliveryState = :state, " +
            "deliveredViaTransport = COALESCE(:transport, deliveredViaTransport), " +
            "relayedThroughHopCount = COALESCE(:relayedHopCount, relayedThroughHopCount) " +
            "WHERE messageId = :messageId",
    )
    suspend fun updateState(messageId: String, state: String, transport: String?, relayedHopCount: Int?)
}
