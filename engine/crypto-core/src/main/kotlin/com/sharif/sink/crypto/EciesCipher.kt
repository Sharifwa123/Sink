package com.sharif.sink.crypto

import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sealed envelope produced by [EciesCipher.encrypt]. Everything in here is
 * safe for a relay to see — it is exactly what a relay forwards.
 */
data class SealedEnvelope(
    val ephemeralPublicKey: ByteArray,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SealedEnvelope) return false
        return ephemeralPublicKey.contentEquals(other.ephemeralPublicKey) &&
            nonce.contentEquals(other.nonce) &&
            ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = ephemeralPublicKey.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}

class DecryptionFailedException(cause: Throwable) : Exception("Sink message could not be decrypted", cause)

/**
 * A minimal integrated encryption scheme: ephemeral-static ECDH (P-256) →
 * HKDF-SHA256 → AES-256-GCM. Conceptually equivalent to ECIES.
 *
 * This gives forward secrecy for the *sender's* ephemeral key (a compromise
 * of the sender's long-term key later does not expose past ciphertexts to a
 * passive attacker who lacks the ephemeral private key), but not full
 * double-ratchet forward secrecy. That limitation is documented in
 * docs/SECURITY.md rather than overstated.
 */
object EciesCipher {
    private const val AES_ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val NONCE_BYTES = 12
    private const val AES_KEY_BYTES = 32
    private val HKDF_INFO = "sink-message-v1".toByteArray(Charsets.UTF_8)
    val ALGORITHM_ID = "ECDH-P256+HKDF-SHA256+AES-256-GCM"

    private val secureRandom = SecureRandom()

    fun encrypt(recipientAgreementPublicKey: PublicKey, plaintext: ByteArray): SealedEnvelope {
        val ephemeralKeyPair = SinkKeyPairs.generate()
        val sharedSecret = agree(ephemeralKeyPair.private, recipientAgreementPublicKey)

        val ephemeralPublicEncoded = SinkKeyPairs.encodePublicKey(ephemeralKeyPair.public)
        val aesKeyBytes = Hkdf.deriveKey(
            ikm = sharedSecret,
            salt = ephemeralPublicEncoded,
            info = HKDF_INFO,
            outputLength = AES_KEY_BYTES,
        )

        val nonce = ByteArray(NONCE_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(aesKeyBytes, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        val ciphertext = cipher.doFinal(plaintext)

        return SealedEnvelope(
            ephemeralPublicKey = ephemeralPublicEncoded,
            nonce = nonce,
            ciphertext = ciphertext,
        )
    }

    fun decrypt(recipientAgreementPrivateKey: PrivateKey, envelope: SealedEnvelope): ByteArray {
        try {
            val ephemeralPublicKey = SinkKeyPairs.decodePublicKey(envelope.ephemeralPublicKey)
            val sharedSecret = agree(recipientAgreementPrivateKey, ephemeralPublicKey)

            val aesKeyBytes = Hkdf.deriveKey(
                ikm = sharedSecret,
                salt = envelope.ephemeralPublicKey,
                info = HKDF_INFO,
                outputLength = AES_KEY_BYTES,
            )

            val cipher = Cipher.getInstance(AES_ALGORITHM)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(aesKeyBytes, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, envelope.nonce),
            )
            return cipher.doFinal(envelope.ciphertext)
        } catch (e: Exception) {
            // Never propagate raw crypto exceptions (padding oracle surface) to callers —
            // collapse everything to one failure type. Tampered/foreign packets must fail safe.
            throw DecryptionFailedException(e)
        }
    }

    private fun agree(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        return agreement.generateSecret()
    }
}
