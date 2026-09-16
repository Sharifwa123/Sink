package com.sharif.sink.crypto

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

/**
 * Curve choice: NIST P-256 ("secp256r1"), not Curve25519/Ed25519.
 *
 * This is a deliberate compatibility tradeoff, not an oversight: P-256 is
 * supported by every Android API level Sink targets (KeyPairGenerator "EC"
 * has been available since API 1, and Android Keystore has supported
 * hardware-backed EC key generation since API 23), whereas X25519/Ed25519
 * support in Android's default crypto providers and in AndroidKeyStore is
 * inconsistent across OS versions. See docs/SECURITY.md for the full
 * rationale and the upgrade path if a future minSdk bump makes Curve25519
 * safe to require.
 */
private const val CURVE_NAME = "secp256r1"
private const val KEY_ALGORITHM = "EC"

object SinkKeyPairs {

    /** Generates a fresh EC key pair usable for either signing or key agreement. */
    fun generate(): KeyPair {
        val generator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        generator.initialize(ECGenParameterSpec(CURVE_NAME))
        return generator.generateKeyPair()
    }

    /** Encodes a public key to its portable X.509 SubjectPublicKeyInfo bytes. */
    fun encodePublicKey(publicKey: PublicKey): ByteArray = publicKey.encoded

    /** Decodes a public key previously produced by [encodePublicKey]. */
    fun decodePublicKey(encoded: ByteArray): PublicKey {
        val factory = KeyFactory.getInstance(KEY_ALGORITHM)
        return factory.generatePublic(X509EncodedKeySpec(encoded))
    }
}
