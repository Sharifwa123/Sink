package com.sharif.sink.mesh.simulation

import com.sharif.sink.mesh.IncomingPacket
import com.sharif.sink.protocol.DeviceId
import com.sharif.sink.protocol.SinkPacket

/**
 * An in-process stand-in for real radio adjacency: a mutable graph of
 * "who is currently in direct range of whom." Lets tests exercise the real
 * [com.sharif.sink.mesh.RoutingEngine] and [com.sharif.sink.mesh.TransportManager]
 * across a topology of any shape, including changing it mid-test (a node
 * "walking out of range" or "coming back"), without needing physical
 * devices. This is the mesh simulator called for by the product brief
 * ("test A→B→C→D, then C disappears") — the routing behavior it exercises
 * is the same code path used with real transports on device.
 */
class SimulatedMeshNetwork {
    private val adjacency = mutableMapOf<DeviceId, MutableSet<DeviceId>>()
    private val nodes = mutableMapOf<DeviceId, SimulatedTransport>()

    fun register(transport: SimulatedTransport) {
        nodes[transport.selfId] = transport
        adjacency.putIfAbsent(transport.selfId, mutableSetOf())
    }

    /** Makes [a] and [b] directly reachable from one another (symmetric link). */
    fun connect(a: DeviceId, b: DeviceId) {
        adjacency.getOrPut(a) { mutableSetOf() }.add(b)
        adjacency.getOrPut(b) { mutableSetOf() }.add(a)
        pushNeighbors(a)
        pushNeighbors(b)
    }

    /** Simulates a node moving out of range of another, or a device being turned off. */
    fun disconnect(a: DeviceId, b: DeviceId) {
        adjacency[a]?.remove(b)
        adjacency[b]?.remove(a)
        pushNeighbors(a)
        pushNeighbors(b)
    }

    /** Removes [id] from every link, simulating the device disappearing entirely. */
    fun disconnectAll(id: DeviceId) {
        val neighbors = adjacency[id]?.toSet().orEmpty()
        neighbors.forEach { disconnect(id, it) }
    }

    suspend fun deliver(from: DeviceId, to: DeviceId, packet: SinkPacket): Boolean {
        if (to !in adjacency[from].orEmpty()) return false
        val target = nodes[to] ?: return false
        target.receive(IncomingPacket(from, packet))
        return true
    }

    private fun pushNeighbors(id: DeviceId) {
        nodes[id]?.onNeighborsChanged(adjacency[id].orEmpty().toSet())
    }
}
