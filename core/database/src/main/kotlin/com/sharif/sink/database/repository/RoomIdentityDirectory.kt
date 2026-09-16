package com.sharif.sink.database.repository

import com.sharif.sink.crypto.SinkKeyPairs
import com.sharif.sink.database.dao.ContactDao
import com.sharif.sink.mesh.IdentityDirectory
import com.sharif.sink.protocol.DeviceId
import java.security.PublicKey

/**
 * Looks up a peer's public keys from the known-contacts table. A device
 * Sink has never exchanged identity with has no entry here, and the
 * routing engine correctly refuses to trust or decrypt packets claiming
 * to be from it — see RoutingEngine.handleForUs. A blocked contact is
 * treated the same way (keys withheld) rather than only hidden in the UI:
 * a DATA packet from them can't be signature-verified without a key, so
 * RoutingEngine drops it exactly as it would an unknown sender, and
 * sendMessage can't encrypt to them either.
 */
class RoomIdentityDirectory(private val contactDao: ContactDao) : IdentityDirectory {

    override suspend fun signingPublicKeyOf(deviceId: DeviceId): PublicKey? =
        contactDao.get(deviceId.value)
            ?.takeIf { !it.isBlocked }
            ?.signingPublicKey
            ?.let { SinkKeyPairs.decodePublicKey(it) }

    override suspend fun agreementPublicKeyOf(deviceId: DeviceId): PublicKey? =
        contactDao.get(deviceId.value)
            ?.takeIf { !it.isBlocked }
            ?.agreementPublicKey
            ?.let { SinkKeyPairs.decodePublicKey(it) }
}
