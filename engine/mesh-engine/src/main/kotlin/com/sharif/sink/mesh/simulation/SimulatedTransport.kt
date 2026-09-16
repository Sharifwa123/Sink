package com.sharif.sink.mesh.simulation

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

/** [CommunicationTransport] backed by a [SimulatedMeshNetwork] instead of real radios. */
class SimulatedTransport(
    val selfId: DeviceId,
    private val network: SimulatedMeshNetwork,
    override val kind: TransportKind = TransportKind.LOCAL_MESH,
    override val capabilities: TransportCapabilities = TransportCapabilities.LOCAL_MESH_TEXT,
) : CommunicationTransport {

    private val _connectedPeers = MutableStateFlow<Set<DeviceId>>(emptySet())
    override val connectedPeers: StateFlow<Set<DeviceId>> = _connectedPeers

    private val _incomingPackets = MutableSharedFlow<IncomingPacket>(extraBufferCapacity = 64)
    override val incomingPackets: Flow<IncomingPacket> = _incomingPackets

    init {
        network.register(this)
    }

    internal fun onNeighborsChanged(neighbors: Set<DeviceId>) {
        _connectedPeers.value = neighbors
    }

    internal suspend fun receive(incoming: IncomingPacket) {
        _incomingPackets.emit(incoming)
    }

    override suspend fun start() = Unit

    override suspend fun stop() = Unit

    override suspend fun send(peer: DeviceId, packet: SinkPacket): TransportSendResult {
        return if (network.deliver(selfId, peer, packet)) {
            TransportSendResult.Delivered
        } else {
            TransportSendResult.Failed("No direct link from $selfId to $peer")
        }
    }
}
