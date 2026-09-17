package com.sharif.sink.mesh

import com.sharif.sink.protocol.DeviceId
import com.sharif.sink.protocol.SinkPacket
import com.sharif.sink.protocol.TransportKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class NetworkStatus {
    ONLINE,
    LOCAL_MESH,
    NEARBY,
    SMS_FALLBACK,
    OFFLINE,
}

data class ReceivedPacket(val fromPeer: DeviceId, val viaTransport: TransportKind, val packet: SinkPacket)

/**
 * Transports that participate in automatic mesh-style flooding/relay.
 * SMS is deliberately excluded: it is never sent silently as part of a
 * broadcast sweep. It costs the user money, reveals a phone number, and
 * must always be an explicit, user-confirmed action ("Send by SMS"),
 * surfaced only after mesh/internet delivery has failed.
 */
private val MESH_BROADCAST_KINDS = setOf(TransportKind.LOCAL_MESH, TransportKind.NEARBY_DIRECT, TransportKind.INTERNET)

/**
 * Orchestrates the set of available [CommunicationTransport]s: ranks them,
 * fans a packet out to reachable peers, and aggregates peer visibility for
 * the routing engine and the UI's network status indicator.
 *
 * Transport priority is configurable — offline-first means local mesh
 * usually outranks the internet, but this is a default, not a hardcoded
 * assumption (see [setPriority]).
 */
class TransportManager(
    private val transports: List<CommunicationTransport>,
    priority: List<TransportKind> = listOf(
        TransportKind.LOCAL_MESH,
        TransportKind.NEARBY_DIRECT,
        TransportKind.INTERNET,
        TransportKind.SMS,
    ),
    private val scope: CoroutineScope,
) {
    private var priorityOrder: List<TransportKind> = priority
    private val packetListeners = mutableListOf<suspend (ReceivedPacket) -> Unit>()
    private val peerConnectedListeners = mutableListOf<suspend (DeviceId) -> Unit>()
    private val peerDisconnectedListeners = mutableListOf<suspend (DeviceId) -> Unit>()
    private val previousPeers = mutableSetOf<DeviceId>()

    private val _networkStatus = MutableStateFlow(NetworkStatus.OFFLINE)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus

    fun setPriority(newPriority: List<TransportKind>) {
        priorityOrder = newPriority
    }

    fun transportOfKind(kind: TransportKind): CommunicationTransport? = transports.find { it.kind == kind }

    suspend fun start() {
        transports.forEach { transport ->
            transport.start()
            scope.launch {
                transport.incomingPackets.collect { incoming ->
                    val received = ReceivedPacket(incoming.fromPeer, transport.kind, incoming.packet)
                    packetListeners.forEach { it(received) }
                }
            }
            scope.launch {
                transport.connectedPeers.collect {
                    recomputeStatus()
                    notifyNewPeers()
                }
            }
        }
    }

    suspend fun stop() {
        transports.forEach { it.stop() }
    }

    fun registerPacketListener(listener: suspend (ReceivedPacket) -> Unit) {
        packetListeners += listener
    }

    fun registerPeerConnectedListener(listener: suspend (DeviceId) -> Unit) {
        peerConnectedListeners += listener
    }

    fun registerPeerDisconnectedListener(listener: suspend (DeviceId) -> Unit) {
        peerDisconnectedListeners += listener
    }

    /** For each currently reachable peer, the highest-priority mesh-capable transport connected to them. */
    fun reachablePeers(): Map<DeviceId, CommunicationTransport> {
        val ranked = transports
            .filter { it.kind in MESH_BROADCAST_KINDS }
            .sortedBy { priorityOrder.indexOf(it.kind).let { idx -> if (idx == -1) Int.MAX_VALUE else idx } }
        val result = LinkedHashMap<DeviceId, CommunicationTransport>()
        for (transport in ranked) {
            for (peer in transport.connectedPeers.value) {
                result.putIfAbsent(peer, transport)
            }
        }
        return result
    }

    suspend fun sendToPeer(peer: DeviceId, packet: SinkPacket): TransportSendResult {
        val transport = reachablePeers()[peer] ?: return TransportSendResult.Failed("Peer not reachable")
        return transport.send(peer, packet)
    }

    /** Sends [packet] to every currently reachable peer except [excludePeer] (where it came from). */
    suspend fun broadcast(packet: SinkPacket, excludePeer: DeviceId? = null): Set<DeviceId> {
        val delivered = mutableSetOf<DeviceId>()
        for ((peer, transport) in reachablePeers()) {
            if (peer == excludePeer) continue
            if (transport.send(peer, packet) is TransportSendResult.Delivered) {
                delivered += peer
            }
        }
        return delivered
    }

    private fun recomputeStatus() {
        val connectedKinds = transports.filter { it.connectedPeers.value.isNotEmpty() }.map { it.kind }.toSet()
        _networkStatus.value = when {
            TransportKind.INTERNET in connectedKinds -> NetworkStatus.ONLINE
            TransportKind.LOCAL_MESH in connectedKinds -> NetworkStatus.LOCAL_MESH
            TransportKind.NEARBY_DIRECT in connectedKinds -> NetworkStatus.NEARBY
            TransportKind.SMS in connectedKinds -> NetworkStatus.SMS_FALLBACK
            else -> NetworkStatus.OFFLINE
        }
    }

    private suspend fun notifyNewPeers() {
        val current = transports.flatMap { it.connectedPeers.value }.toSet()
        val newlyConnected = current - previousPeers
        val newlyDisconnected = previousPeers - current
        previousPeers.clear()
        previousPeers += current
        newlyConnected.forEach { peer -> peerConnectedListeners.forEach { it(peer) } }
        newlyDisconnected.forEach { peer -> peerDisconnectedListeners.forEach { it(peer) } }
    }
}
