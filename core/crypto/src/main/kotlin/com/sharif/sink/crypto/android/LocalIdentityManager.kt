package com.sharif.sink.crypto.android

import com.sharif.sink.crypto.Fingerprint
import com.sharif.sink.crypto.SinkKeyPairs
import com.sharif.sink.mesh.LocalIdentity
import com.sharif.sink.protocol.DeviceId
import com.sharif.sink.protocol.DeviceIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the device's identity lifecycle end to end: creates it once on
 * first install, never regenerates it silently afterward, and hands out
 * the [LocalIdentity] the mesh routing engine needs without ever exposing
 * raw private key bytes to callers outside this module.
 */
@Singleton
class LocalIdentityManager @Inject constructor(
    @ApplicationContext context: android.content.Context,
) {
    private val signingKeyProvider = AndroidSigningKeyProvider()
    private val wrappingKeyProvider = WrappingKeyProvider()
    private val store = EncryptedIdentityStore(context)
    private val mutex = Mutex()

    @Volatile
    private var cached: LocalIdentity? = null

    suspend fun getOrCreateIdentity(defaultDisplayName: String = "Sink User"): LocalIdentity = mutex.withLock {
        cached?.let { return@withLock it }

        val signingKeyPair = signingKeyProvider.getOrCreateSigningKeyPair()
        val persisted = store.load()

        val agreementKeyPair: KeyPair = if (persisted != null) {
            val privateBytes = wrappingKeyProvider.unwrap(persisted.wrappedAgreementPrivateKey)
            val factory = KeyFactory.getInstance("EC")
            val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(privateBytes))
            val publicKey = SinkKeyPairs.decodePublicKey(persisted.agreementPublicKey)
            KeyPair(publicKey, privateKey)
        } else {
            val generated = SinkKeyPairs.generate()
            val wrapped = wrappingKeyProvider.wrap(generated.private.encoded)
            store.save(
                EncryptedIdentityStore.PersistedIdentity(
                    displayName = defaultDisplayName,
                    signingPublicKey = SinkKeyPairs.encodePublicKey(signingKeyPair.public),
                    agreementPublicKey = SinkKeyPairs.encodePublicKey(generated.public),
                    wrappedAgreementPrivateKey = wrapped,
                ),
            )
            generated
        }

        val deviceId = DeviceId(Fingerprint.of(signingKeyPair.public))
        LocalIdentity(deviceId, signingKeyPair, agreementKeyPair).also { cached = it }
    }

    fun displayName(): String = store.load()?.displayName ?: "Sink User"

    fun setDisplayName(name: String) = store.updateDisplayName(name)

    /** What this device advertises to peers during discovery. */
    suspend fun deviceIdentity(): DeviceIdentity {
        val identity = getOrCreateIdentity()
        return DeviceIdentity(
            deviceId = identity.deviceId,
            signingPublicKey = SinkKeyPairs.encodePublicKey(identity.signingKeyPair.public),
            agreementPublicKey = SinkKeyPairs.encodePublicKey(identity.agreementKeyPair.public),
            displayName = displayName(),
        )
    }

    /** Safety-number-style fingerprint for out-of-band contact verification. */
    suspend fun fingerprint(): String = Fingerprint.of(getOrCreateIdentity().signingKeyPair.public)
}
