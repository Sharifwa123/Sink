package com.sharif.sink.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val conversationId: String,
    val peerDeviceId: String,
    val peerDisplayName: String,
    val lastMessagePreview: String?,
    val lastMessageAtEpochMillis: Long?,
    val unreadCount: Int,
)
