package com.sharif.sink.crypto

import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

/** ECDSA over SHA-256. Proves that a packet's sender-claimed identity actually authored it. */
object Signatures {
    private const val ALGORITHM = "SHA256withECDSA"

    fun sign(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val signature = Signature.getInstance(ALGORITHM)
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    fun verify(publicKey: PublicKey, data: ByteArray, signatureBytes: ByteArray): Boolean {
        return try {
            val signature = Signature.getInstance(ALGORITHM)
            signature.initVerify(publicKey)
            signature.update(data)
            signature.verify(signatureBytes)
        } catch (_ : Exception) {
            // Malformed signature bytes must be treated as "does not verify", never crash the
            // caller — packets on the mesh are untrusted input.
            false
        }
    }
}
