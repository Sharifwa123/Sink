package com.sharif.sink.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Wire format for exchanging a [DeviceIdentity] during the HELLO handshake — see RoutingEngine. */
object DeviceIdentityCodec {

    fun encode(identity: DeviceIdentity): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(identity.protocolVersion)
            d.writeUTF(identity.deviceId.value)
            d.writeUTF(identity.displayName)
            d.writeByteArray(identity.signingPublicKey)
            d.writeByteArray(identity.agreementPublicKey)
            d.writeInt(identity.capabilities.size)
            identity.capabilities.forEach { d.writeUTF(it.name) }
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): DeviceIdentity {
        DataInputStream(ByteArrayInputStream(bytes)).use { d ->
            val protocolVersion = d.readInt()
            val deviceId = DeviceId(d.readUTF())
            val displayName = d.readUTF()
            val signingPublicKey = d.readLengthPrefixedBytes()
            val agreementPublicKey = d.readLengthPrefixedBytes()
            val capabilityCount = d.readInt()
            val capabilities = (0 until capabilityCount).map { TransportKind.valueOf(d.readUTF()) }.toSet()
            return DeviceIdentity(
                deviceId = deviceId,
                signingPublicKey = signingPublicKey,
                agreementPublicKey = agreementPublicKey,
                displayName = displayName,
                protocolVersion = protocolVersion,
                capabilities = capabilities,
            )
        }
    }

    private fun DataOutputStream.writeByteArray(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readLengthPrefixedBytes(): ByteArray {
        val length = readInt()
        val buffer = ByteArray(length)
        readFully(buffer)
        return buffer
    }
}
