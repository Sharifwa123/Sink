package com.sharif.sink.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.sharif.sink.database.dao.ContactDao
import com.sharif.sink.database.dao.ConversationDao
import com.sharif.sink.database.dao.MessageDao
import com.sharif.sink.database.dao.PeerDao
import com.sharif.sink.database.dao.PendingInviteDao
import com.sharif.sink.database.entity.ContactEntity
import com.sharif.sink.database.entity.ConversationEntity
import com.sharif.sink.database.entity.MessageEntity
import com.sharif.sink.database.entity.PeerEntity
import com.sharif.sink.database.entity.PendingInviteEntity

@Database(
    entities = [
        MessageEntity::class,
        ConversationEntity::class,
        ContactEntity::class,
        PeerEntity::class,
        PendingInviteEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class SinkDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun contactDao(): ContactDao
    abstract fun peerDao(): PeerDao
    abstract fun pendingInviteDao(): PendingInviteDao
}

/** Adds the manual-add-contact / SMS-invite table; everything else is untouched. */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_invites` (
                `phoneNumber` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `invitedAtEpochMillis` INTEGER NOT NULL,
                `lastInviteSentEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`phoneNumber`)
            )
            """.trimIndent(),
        )
    }
}
