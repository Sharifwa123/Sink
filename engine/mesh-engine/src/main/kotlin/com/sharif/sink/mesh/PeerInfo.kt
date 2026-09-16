package com.sharif.sink.mesh

import com.sharif.sink.protocol.DeviceIdentity
import com.sharif.sink.protocol.TransportKind

enum class PeerConnectionState {
    DISCOVERED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    LOST,
}

/**
 * Runtime knowledge about a peer, as distinct from its cryptographic
 * [DeviceIdentity]: this is what changes constantly (seen/lost, which
 * transport) rather than what's a stable fact about the peer.
 */
data class PeerInfo(
    val identity: DeviceIdentity,
    val lastSeenEpochMillis: Long,
    val connectionState: PeerConnectionState,
    val transport: TransportKind,
)
