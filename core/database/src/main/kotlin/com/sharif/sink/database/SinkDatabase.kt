package com.sharif.sink.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sharif.sink.database.dao.ContactDao
import com.sharif.sink.database.dao.ConversationDao
import com.sharif.sink.database.dao.MessageDao
import com.sharif.sink.database.dao.PeerDao
import com.sharif.sink.database.entity.ContactEntity
import com.sharif.sink.database.entity.ConversationEntity
import com.sharif.sink.database.entity.MessageEntity
import com.sharif.sink.database.entity.PeerEntity

@Database(
    entities = [MessageEntity::class, ConversationEntity::class, ContactEntity::class, PeerEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SinkDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun contactDao(): ContactDao
    abstract fun peerDao(): PeerDao
}
