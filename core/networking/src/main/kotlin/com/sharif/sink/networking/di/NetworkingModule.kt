package com.sharif.sink.networking.di

import android.content.Context
import com.sharif.sink.common.di.ApplicationScope
import com.sharif.sink.crypto.android.LocalIdentityManager
import com.sharif.sink.database.dao.ContactDao
import com.sharif.sink.database.dao.ConversationDao
import com.sharif.sink.database.dao.MessageDao
import com.sharif.sink.database.repository.RoomIdentityDirectory
import com.sharif.sink.database.repository.RoomMessageQueue
import com.sharif.sink.logging.SinkLogger
import com.sharif.sink.mesh.CommunicationTransport
import com.sharif.sink.mesh.IdentityDirectory
import com.sharif.sink.mesh.LocalIdentity
import com.sharif.sink.mesh.MessageQueue
import com.sharif.sink.mesh.RoutingEngine
import com.sharif.sink.mesh.TransportManager
import com.sharif.sink.networking.transport.InternetTransport
import com.sharif.sink.networking.transport.NearbyTransport
import com.sharif.sink.networking.transport.SmsTransport
import com.sharif.sink.permissions.PermissionChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkingModule {

    /**
     * Identity bootstrap is a one-time, sub-100ms local Keystore/EC operation on first
     * launch (and a cheap unwrap on every later launch) — not a network call — so resolving
     * it synchronously the first time this singleton is requested is a deliberate, bounded
     * tradeoff rather than a hidden blocking call. See docs/ANDROID_LIMITATIONS.md.
     */
    @Provides
    @Singleton
    fun provideLocalIdentity(manager: LocalIdentityManager): LocalIdentity = runBlocking {
        manager.getOrCreateIdentity()
    }

    @Provides
    @Singleton
    fun provideMessageQueue(
        messageDao: MessageDao,
        conversationDao: ConversationDao,
        localIdentity: LocalIdentity,
    ): MessageQueue = RoomMessageQueue(messageDao, conversationDao) { localIdentity.deviceId }

    @Provides
    @Singleton
    fun provideIdentityDirectory(contactDao: ContactDao): IdentityDirectory = RoomIdentityDirectory(contactDao)

    @Provides
    @Singleton
    fun provideTransports(
        @ApplicationContext context: Context,
        localIdentity: LocalIdentity,
        logger: SinkLogger,
        contactDao: ContactDao,
        permissionChecker: PermissionChecker,
    ): List<@JvmSuppressWildcards CommunicationTransport> = listOf(
        NearbyTransport(context, localIdentity.deviceId, logger),
        SmsTransport(contactDao, permissionChecker, logger),
        InternetTransport(),
    )

    @Provides
    @Singleton
    fun provideTransportManager(
        transports: List<@JvmSuppressWildcards CommunicationTransport>,
        @ApplicationScope scope: CoroutineScope,
    ): TransportManager = TransportManager(transports, scope = scope)

    @Provides
    @Singleton
    fun provideRoutingEngine(
        localIdentity: LocalIdentity,
        transportManager: TransportManager,
        identityDirectory: IdentityDirectory,
        messageQueue: MessageQueue,
        @ApplicationScope scope: CoroutineScope,
    ): RoutingEngine = RoutingEngine(
        localIdentity = localIdentity,
        transportManager = transportManager,
        identityDirectory = identityDirectory,
        messageQueue = messageQueue,
        scope = scope,
    )
}
