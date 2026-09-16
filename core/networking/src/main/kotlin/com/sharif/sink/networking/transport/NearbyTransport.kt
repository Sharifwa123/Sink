package com.sharif.sink.networking.transport

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.Strategy
import com.sharif.sink.logging.SinkLogger
import com.sharif.sink.mesh.CommunicationTransport
import com.sharif.sink.mesh.IncomingPacket
import com.sharif.sink.mesh.TransportCapabilities
import com.sharif.sink.mesh.TransportSendResult
import com.sharif.sink.protocol.DeviceId
import com.sharif.sink.protocol.PacketCodec
import com.sharif.sink.protocol.SinkPacket
import com.sharif.sink.protocol.TransportKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

private const val SERVICE_ID = "com.sharif.sink.mesh"
private const val TAG = "NearbyTransport"

/**
 * Local mesh transport over Google Play Services Nearby Connections.
 *
 * IMPORTANT — what this class is *not*: Nearby Connections gives Sink
 * device-to-device discovery and direct (one-hop) links over whatever
 * radio it picks (Bluetooth/BLE/Wi-Fi), nothing more. It does not provide
 * multi-hop routing, store-and-forward, deduplication, or any concept of
 * "the mesh" — all of that lives in [com.sharif.sink.mesh.RoutingEngine],
 * which treats this transport exactly like [com.sharif.sink.mesh.simulation.SimulatedTransport]
 * in tests. This class's only job is turning one physical link into the
 * [CommunicationTransport] contract.
 *
 * P2P_CLUSTER is used (not P2P_STAR or P2P_POINT_TO_POINT) because Sink
 * needs a device to hold multiple simultaneous direct connections to act
 * as a relay — the number it can actually sustain is a platform/hardware
 * limit, not something this code controls (see docs/ANDROID_LIMITATIONS.md).
 */
class NearbyTransport(
    context: Context,
    private val localDeviceId: DeviceId,
    private val logger: SinkLogger,
) : CommunicationTransport {

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)

    override val kind: TransportKind = TransportKind.LOCAL_MESH
    override val capabilities: TransportCapabilities = TransportCapabilities.LOCAL_MESH_TEXT

    private val _connectedPeers = MutableStateFlow<Set<DeviceId>>(emptySet())
    override val connectedPeers: StateFlow<Set<DeviceId>> = _connectedPeers

    private val _incomingPackets = MutableSharedFlow<IncomingPacket>(extraBufferCapacity = 64)
    override val incomingPackets: Flow<IncomingPacket> = _incomingPackets

    // endpointId (Nearby's transient session handle) <-> stable DeviceId, established once a
    // connection actually succeeds (the endpoint's advertised name is its DeviceId).
    private val endpointToDeviceId = HashMap<String, DeviceId>()
    private val deviceIdToEndpoint = HashMap<DeviceId, String>()

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Transport-level auto-accept: establishing a link is not the same as trusting
            // message content. RoutingEngine independently drops any DATA packet whose sender
            // it can't verify — see docs/THREAT_MODEL.md on what this transport does and does
            // not protect against.
            endpointToDeviceId[endpointId] = DeviceId(info.endpointName)
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.statusCode == ConnectionsStatusCodes.STATUS_OK) {
                val deviceId = endpointToDeviceId[endpointId] ?: return
                deviceIdToEndpoint[deviceId] = endpointId
                _connectedPeers.value = _connectedPeers.value + deviceId
            } else {
                endpointToDeviceId.remove(endpointId)
            }
        }

        override fun onDisconnected(endpointId: String) {
            val deviceId = endpointToDeviceId.remove(endpointId) ?: return
            deviceIdToEndpoint.remove(deviceId)
            _connectedPeers.value = _connectedPeers.value - deviceId
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId != SERVICE_ID) return
            client.requestConnection(localDeviceId.value, endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) {
            val deviceId = endpointToDeviceId.remove(endpointId) ?: return
            deviceIdToEndpoint.remove(deviceId)
            _connectedPeers.value = _connectedPeers.value - deviceId
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            val fromPeer = endpointToDeviceId[endpointId] ?: return
            val packet: SinkPacket = try {
                PacketCodec.decode(bytes)
            } catch (e: Exception) {
                logger.w(TAG, "Dropped malformed packet from $fromPeer: ${e.javaClass.simpleName}")
                return
            }
            _incomingPackets.tryEmit(IncomingPacket(fromPeer, packet))
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    override suspend fun start() {
        try {
            val advertisingOptions = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
            client.startAdvertising(localDeviceId.value, SERVICE_ID, connectionLifecycleCallback, advertisingOptions).await()

            val discoveryOptions = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
            client.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions).await()
        } catch (e: Exception) {
            // Missing runtime permission, radios off, or no Play Services — fail closed, not silently.
            logger.w(TAG, "Nearby start failed: ${e.javaClass.simpleName}")
        }
    }

    override suspend fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        endpointToDeviceId.clear()
        deviceIdToEndpoint.clear()
        _connectedPeers.value = emptySet()
    }

    override suspend fun send(peer: DeviceId, packet: SinkPacket): TransportSendResult {
        val endpointId = deviceIdToEndpoint[peer] ?: return TransportSendResult.Failed("No Nearby link to $peer")
        return try {
            client.sendPayload(endpointId, Payload.fromBytes(PacketCodec.encode(packet))).await()
            TransportSendResult.Delivered
        } catch (e: Exception) {
            TransportSendResult.Failed(e.message ?: "Nearby send failed")
        }
    }
}
