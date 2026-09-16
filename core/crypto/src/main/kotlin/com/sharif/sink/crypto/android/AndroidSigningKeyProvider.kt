package com.sharif.sink.crypto.android

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.cert.Certificate

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val SIGNING_KEY_ALIAS = "sink_signing_key_v1"
private const val CURVE_NAME = "secp256r1"

/**
 * The device's long-term signing identity, generated once and held
 * non-exportable in Android Keystore (StrongBox-backed where the device
 * supports it). This key never leaves secure hardware/TEE — only
 * signing/verification operations are performed through it, per
 * docs/SECURITY.md. See that document for why the *agreement* key (used
 * for message encryption) cannot use the same approach on every supported
 * Android version.
 */
class AndroidSigningKeyProvider {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Creates the signing key on first call; returns the existing one afterwards. Never regenerates silently. */
    fun getOrCreateSigningKeyPair(): KeyPair {
        keyStore.getEntry(SIGNING_KEY_ALIAS, null)?.let {
            val publicKey = keyStore.getCertificate(SIGNING_KEY_ALIAS)?.publicKey
            val privateKey = (it as? KeyStore.PrivateKeyEntry)?.privateKey
            if (publicKey != null && privateKey != null) {
                return KeyPair(publicKey, privateKey)
            }
        }
        return generate(useStrongBox = true)
    }

    private fun generate(useStrongBox: Boolean): KeyPair {
        val purpose = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        val specBuilder = KeyGenParameterSpec.Builder(SIGNING_KEY_ALIAS, purpose)
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec(CURVE_NAME))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)

        if (useStrongBox && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            specBuilder.setIsStrongBoxBacked(true)
        }

        return try {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            generator.initialize(specBuilder.build())
            generator.generateKeyPair()
        } catch (e: StrongBoxUnavailableException) {
            // Not every device has a StrongBox secure element — fall back to the TEE-backed key.
            generate(useStrongBox = false)
        }
    }

    fun publicKey(): PublicKey? = certificate()?.publicKey

    private fun certificate(): Certificate? = keyStore.getCertificate(SIGNING_KEY_ALIAS)
}
