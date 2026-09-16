package com.sharif.sink.mesh

import com.sharif.sink.protocol.DeviceId
import com.sharif.sink.protocol.SinkPacket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface TransportSendResult {
    /** The directly connected peer's link layer accepted the packet. Not the same as end delivery. */
    data object Delivered : TransportSendResult
    data class Failed(val reason: String) : TransportSendResult
}

/** A packet as received directly from a one-hop neighbor, before any relay decision is made. */
data class IncomingPacket(val fromPeer: DeviceId, val packet: SinkPacket)

/**
 * A single communication path Sink can use: local mesh, a direct nearby
 * peer link, the internet, or SMS. [RoutingEngine] and [TransportManager]
 * never assume a specific transport is present — every implementation must
 * honestly report what it can currently do.
 *
 * Implementations must not block their caller: connection setup, sending,
 * and receiving are all suspend/Flow based.
 */
interface CommunicationTransport {
    val kind: com.sharif.sink.protocol.TransportKind
    val capabilities: TransportCapabilities

    /** Peers this transport currently has a live, direct (one-hop) link to. */
    val connectedPeers: StateFlow<Set<DeviceId>>

    /** Packets arriving directly over this transport's active links. */
    val incomingPackets: Flow<IncomingPacket>

    suspend fun start()
    suspend fun stop()

    /** Attempts to hand [packet] to [peer] over this transport's current link, if any. */
    suspend fun send(peer: DeviceId, packet: SinkPacket): TransportSendResult
}
