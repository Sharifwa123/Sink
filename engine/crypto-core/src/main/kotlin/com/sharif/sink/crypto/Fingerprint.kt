package com.sharif.sink.crypto

import java.security.MessageDigest
import java.security.PublicKey

/**
 * Human-comparable identity fingerprint ("safety number") for out-of-band
 * contact verification (QR code / manual comparison). SHA-256 of the
 * encoded public key, rendered as space-separated groups of digits.
 */
object Fingerprint {
    fun of(publicKey: PublicKey): String = of(publicKey.encoded)

    fun of(encodedPublicKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(encodedPublicKey)
        // Render as decimal groups (Signal-style) rather than raw hex: easier for two humans
        // to read aloud and compare over a phone call or in person.
        val builder = StringBuilder()
        for (i in 0 until 24 step 4) {
            val chunk = ((digest[i].toInt() and 0xFF) shl 24) or
                ((digest[i + 1].toInt() and 0xFF) shl 16) or
                ((digest[i + 2].toInt() and 0xFF) shl 8) or
                (digest[i + 3].toInt() and 0xFF)
            val fiveDigits = (chunk.toLong() and 0xFFFFFFFFL) % 100000
            builder.append(fiveDigits.toString().padStart(5, '0'))
            if (i < 20) builder.append(' ')
        }
        return builder.toString()
    }
}
