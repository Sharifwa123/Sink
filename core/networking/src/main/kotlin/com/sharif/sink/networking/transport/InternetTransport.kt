package com.sharif.sink.networking.transport

import com.sharif.sink.mesh.CommunicationTransport
import com.sharif.sink.mesh.IncomingPacket
import com.sharif.sink.mesh.TransportCapabilities
import com.sharif.sink.mesh.TransportSendResult
import com.sharif.sink.protocol.DeviceId
import com.sharif.sink.protocol.SinkPacket
import com.sharif.sink.protocol.TransportKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Placeholder for the optional internet path (product brief §20/§49: the
 * core mesh must not depend on a backend, and Sink must not pretend to
 * have remote connectivity it doesn't). This transport honestly reports
 * zero connected peers and fails every send rather than simulating a
 * connection — wiring it up requires a signaling/relay service that is
 * explicitly out of scope for this MVP. See docs/IMPLEMENTATION_STATUS.md.
 */
class InternetTransport : CommunicationTransport {
    override val kind: TransportKind = TransportKind.INTERNET
    override val capabilities: TransportCapabilities = TransportCapabilities.INTERNET_RICH
    override val connectedPeers: StateFlow<Set<DeviceId>> = MutableStateFlow(emptySet())
    override val incomingPackets: Flow<IncomingPacket> = MutableSharedFlow()

    override suspend fun start() = Unit
    override suspend fun stop() = Unit

    override suspend fun send(peer: DeviceId, packet: SinkPacket): TransportSendResult =
        TransportSendResult.Failed("Internet transport is not implemented in this build")
}
