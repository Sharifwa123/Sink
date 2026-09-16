package com.sharif.sink.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sharif.sink.database.entity.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Query("SELECT * FROM conversations WHERE conversationId = :conversationId")
    suspend fun get(conversationId: String): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY lastMessageAtEpochMillis DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("UPDATE conversations SET lastMessagePreview = :preview, lastMessageAtEpochMillis = :atEpochMillis WHERE conversationId = :conversationId")
    suspend fun updateLastMessage(conversationId: String, preview: String, atEpochMillis: Long)

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE conversationId = :conversationId")
    suspend fun incrementUnread(conversationId: String)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE conversationId = :conversationId")
    suspend fun clearUnread(conversationId: String)
}
