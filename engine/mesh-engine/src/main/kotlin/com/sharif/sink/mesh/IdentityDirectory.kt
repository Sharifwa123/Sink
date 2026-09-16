package com.sharif.sink.mesh

import com.sharif.sink.protocol.DeviceId
import java.security.KeyPair
import java.security.PublicKey

/**
 * Lookup of other devices' public keys, as known locally (from discovery
 * and/or explicit contact exchange). The routing engine never trusts a
 * claimed identity it can't look up here — an unknown sender's packet is
 * dropped, never processed as if authenticated.
 */
interface IdentityDirectory {
    suspend fun signingPublicKeyOf(deviceId: DeviceId): PublicKey?
    suspend fun agreementPublicKeyOf(deviceId: DeviceId): PublicKey?
}

/** This device's own identity keys. Private key handling on Android is Keystore-backed (see core:crypto). */
class LocalIdentity(
    val deviceId: DeviceId,
    val signingKeyPair: KeyPair,
    val agreementKeyPair: KeyPair,
)
