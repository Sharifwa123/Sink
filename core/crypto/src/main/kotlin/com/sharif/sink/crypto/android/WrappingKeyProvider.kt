package com.sharif.sink.crypto.android

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val WRAPPING_KEY_ALIAS = "sink_identity_wrapping_key_v1"
private const val GCM_TAG_BITS = 128
private const val NONCE_BYTES = 12

/**
 * Android Keystore ECDH (key-agreement purpose) is only available from API
 * 31 — Sink's minSdk is 26, so the message-encryption key pair is
 * generated in software (see LocalIdentityManager) rather than inside
 * Keystore. To still satisfy "never store a private key as plaintext",
 * this hardware-backed AES-256-GCM key encrypts that software key's bytes
 * before they touch disk. This is envelope encryption, not full
 * hardware custody of the agreement key — documented explicitly in
 * docs/SECURITY.md rather than implied to be equivalent to the signing key's
 * protection.
 */
class WrappingKeyProvider {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(WRAPPING_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                WRAPPING_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    /** Returns nonce + ciphertext (GCM tag included), suitable for storing as one opaque blob. */
    fun wrap(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val nonce = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return nonce + ciphertext
    }

    fun unwrap(wrapped: ByteArray): ByteArray {
        val nonce = wrapped.copyOfRange(0, NONCE_BYTES)
        val ciphertext = wrapped.copyOfRange(NONCE_BYTES, wrapped.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, nonce))
        return cipher.doFinal(ciphertext)
    }
}
