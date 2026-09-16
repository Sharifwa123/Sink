package com.sharif.sink.database.di

import android.content.Context
import androidx.room.Room
import com.sharif.sink.database.SinkDatabase
import com.sharif.sink.database.dao.ContactDao
import com.sharif.sink.database.dao.ConversationDao
import com.sharif.sink.database.dao.MessageDao
import com.sharif.sink.database.dao.PeerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SinkDatabase =
        Room.databaseBuilder(context, SinkDatabase::class.java, "sink.db")
            // No destructive fallback: a schema change without a real migration should fail
            // loudly in development rather than silently delete the user's messages.
            .build()

    @Provides
    fun provideMessageDao(database: SinkDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideConversationDao(database: SinkDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideContactDao(database: SinkDatabase): ContactDao = database.contactDao()

    @Provides
    fun providePeerDao(database: SinkDatabase): PeerDao = database.peerDao()
}
