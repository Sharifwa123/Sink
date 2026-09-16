package com.sharif.sink.networking.mesh

import com.sharif.sink.common.di.ApplicationScope
import com.sharif.sink.database.dao.ContactDao
import com.sharif.sink.database.dao.PeerDao
import com.sharif.sink.database.entity.ContactEntity
import com.sharif.sink.database.entity.PeerEntity
import com.sharif.sink.mesh.PeerConnectionState
import com.sharif.sink.mesh.RoutingEngine
import com.sharif.sink.mesh.TransportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges [RoutingEngine.discoveredIdentities] (a verified-on-first-contact
 * HELLO handshake result) into the local contacts table, so Discovery and
 * Contacts screens have something real to show — never a fabricated peer.
 * A contact created this way is deliberately [ContactEntity.isVerified] =
 * false: the handshake proves internal consistency of the claim, not that
 * a human has confirmed it out-of-band (see docs/SECURITY.md).
 */
@Singleton
class PeerIdentityPersister @Inject constructor(
    private val routingEngine: RoutingEngine,
    private val transportManager: TransportManager,
    private val contactDao: ContactDao,
    private val peerDao: PeerDao,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun start() {
        transportManager.registerPeerDisconnectedListener { deviceId ->
            peerDao.get(deviceId.value)?.let { peer ->
                peerDao.upsert(peer.copy(connectionState = PeerConnectionState.DISCONNECTED.name))
            }
        }

        routingEngine.discoveredIdentities
            .onEach { identity ->
                val now = System.currentTimeMillis()
                val existing = contactDao.get(identity.deviceId.value)
                contactDao.upsert(
                    ContactEntity(
                        deviceId = identity.deviceId.value,
                        displayName = existing?.displayName?.takeIf { it.isNotBlank() } ?: identity.displayName,
                        signingPublicKey = identity.signingPublicKey,
                        agreementPublicKey = identity.agreementPublicKey,
                        fingerprint = com.sharif.sink.crypto.Fingerprint.of(identity.signingPublicKey),
                        isVerified = existing?.isVerified ?: false,
                        isBlocked = existing?.isBlocked ?: false,
                        addedAtEpochMillis = existing?.addedAtEpochMillis ?: now,
                        phoneNumberForSms = existing?.phoneNumberForSms,
                    ),
                )
                peerDao.upsert(
                    PeerEntity(
                        deviceId = identity.deviceId.value,
                        displayName = identity.displayName,
                        lastSeenEpochMillis = now,
                        connectionState = PeerConnectionState.CONNECTED.name,
                        transport = identity.capabilities.firstOrNull()?.name ?: "LOCAL_MESH",
                    ),
                )
            }
            .launchIn(scope)
    }
}
