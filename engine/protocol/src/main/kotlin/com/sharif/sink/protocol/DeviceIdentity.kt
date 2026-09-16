package com.sharif.sink.protocol

/**
 * Stable, exchangeable description of a Sink device: what a device
 * advertises about itself during discovery, and what's stored about a
 * peer once known. Deliberately excludes phone number or any carrier
 * identity — [DeviceId] is derived from a public key fingerprint.
 *
 * Runtime-changing state (last seen, connection state) is intentionally
 * *not* here — see `PeerInfo` in engine:mesh-engine — this type is the
 * stable identity fact, not the live session state.
 */
data class DeviceIdentity(
    val deviceId: DeviceId,
    val signingPublicKey: ByteArray,
    val agreementPublicKey: ByteArray,
    val displayName: String,
    val protocolVersion: Int = PROTOCOL_VERSION,
    val capabilities: Set<TransportKind> = emptySet(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceIdentity) return false
        return deviceId == other.deviceId &&
            signingPublicKey.contentEquals(other.signingPublicKey) &&
            agreementPublicKey.contentEquals(other.agreementPublicKey) &&
            displayName == other.displayName &&
            protocolVersion == other.protocolVersion &&
            capabilities == other.capabilities
    }

    override fun hashCode(): Int {
        var result = deviceId.hashCode()
        result = 31 * result + signingPublicKey.contentHashCode()
        result = 31 * result + agreementPublicKey.contentHashCode()
        result = 31 * result + displayName.hashCode()
        return result
    }
}
