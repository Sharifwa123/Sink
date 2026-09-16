package com.sharif.sink.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** RFC 5869 HKDF over HMAC-SHA256. Used to turn a raw ECDH shared secret into a uniform AES key. */
object Hkdf {
    private const val MAC_ALGORITHM = "HmacSHA256"
    private const val HASH_LEN = 32

    fun deriveKey(ikm: ByteArray, salt: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        val prk = extract(salt, ikm)
        return expand(prk, info, outputLength)
    }

    private fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance(MAC_ALGORITHM)
        val saltKey = if (salt.isEmpty()) ByteArray(HASH_LEN) else salt
        mac.init(SecretKeySpec(saltKey, MAC_ALGORITHM))
        return mac.doFinal(ikm)
    }

    private fun expand(prk: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        val mac = Mac.getInstance(MAC_ALGORITHM)
        mac.init(SecretKeySpec(prk, MAC_ALGORITHM))

        val output = ByteArray(outputLength)
        var previousBlock = ByteArray(0)
        var generated = 0
        var counter = 1
        while (generated < outputLength) {
            mac.reset()
            mac.update(previousBlock)
            mac.update(info)
            mac.update(counter.toByte())
            previousBlock = mac.doFinal()
            val toCopy = minOf(HASH_LEN, outputLength - generated)
            System.arraycopy(previousBlock, 0, output, generated, toCopy)
            generated += toCopy
            counter++
        }
        return output
    }
}
