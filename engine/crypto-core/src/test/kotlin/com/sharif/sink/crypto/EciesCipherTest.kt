package com.sharif.sink.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class EciesCipherTest {

    @Test
    fun `recipient can decrypt what sender encrypted`() {
        val recipientKeyPair = SinkKeyPairs.generate()
        val plaintext = "Sink message payload".toByteArray()

        val envelope = EciesCipher.encrypt(recipientKeyPair.public, plaintext)
        val decrypted = EciesCipher.decrypt(recipientKeyPair.private, envelope)

        assertContentEquals(plaintext, decrypted)
    }

    @Test
    fun `wrong recipient private key fails to decrypt`() {
        val realRecipient = SinkKeyPairs.generate()
        val attacker = SinkKeyPairs.generate()
        val envelope = EciesCipher.encrypt(realRecipient.public, "secret".toByteArray())

        assertFailsWith<DecryptionFailedException> {
            EciesCipher.decrypt(attacker.private, envelope)
        }
    }

    @Test
    fun `tampered ciphertext fails authentication`() {
        val recipient = SinkKeyPairs.generate()
        val envelope = EciesCipher.encrypt(recipient.public, "secret".toByteArray())
        val tamperedCiphertext = envelope.ciphertext.copyOf()
        tamperedCiphertext[0] = tamperedCiphertext[0].inc()
        val tampered = envelope.copy(ciphertext = tamperedCiphertext)

        assertFailsWith<DecryptionFailedException> {
            EciesCipher.decrypt(recipient.private, tampered)
        }
    }

    @Test
    fun `each encryption uses a fresh nonce and ephemeral key`() {
        val recipient = SinkKeyPairs.generate()
        val first = EciesCipher.encrypt(recipient.public, "same plaintext".toByteArray())
        val second = EciesCipher.encrypt(recipient.public, "same plaintext".toByteArray())

        assertNotEquals(first, second)
    }

    @Test
    fun `signatures verify only for the correct key and untampered data`() {
        val signer = SinkKeyPairs.generate()
        val impostor = SinkKeyPairs.generate()
        val data = "packet-canonical-bytes".toByteArray()

        val signature = Signatures.sign(signer.private, data)

        kotlin.test.assertTrue(Signatures.verify(signer.public, data, signature))
        kotlin.test.assertFalse(Signatures.verify(impostor.public, data, signature))
        kotlin.test.assertFalse(Signatures.verify(signer.public, "different-data".toByteArray(), signature))
    }

    @Test
    fun `fingerprint is deterministic for the same key and differs across keys`() {
        val keyPair = SinkKeyPairs.generate()
        val fingerprintA = Fingerprint.of(keyPair.public)
        val fingerprintB = Fingerprint.of(keyPair.public)
        assertContentEquals(fingerprintA.toByteArray(), fingerprintB.toByteArray())

        val otherKeyPair = SinkKeyPairs.generate()
        assertNotEquals(fingerprintA, Fingerprint.of(otherKeyPair.public))
    }
}
