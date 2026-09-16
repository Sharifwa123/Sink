package com.sharif.sink.mesh.simulation

import com.sharif.sink.crypto.Fingerprint
import com.sharif.sink.crypto.SinkKeyPairs
import com.sharif.sink.mesh.IdentityDirectory
import com.sharif.sink.mesh.InMemoryMessageQueue
import com.sharif.sink.mesh.LocalIdentity
import com.sharif.sink.mesh.MessageQueue
import com.sharif.sink.mesh.PeerRateLimiter
import com.sharif.sink.mesh.RetryPolicy
import com.sharif.sink.mesh.RoutingEngine
import com.sharif.sink.mesh.TransportManager
import com.sharif.sink.protocol.DeviceId
import kotlinx.coroutines.CoroutineScope
import java.security.PublicKey

/** Shared "already exchanged contact info" directory for a simulated topology. */
class MutableIdentityDirectory : IdentityDirectory {
    private val signing = mutableMapOf<DeviceId, PublicKey>()
    private val agreement = mutableMapOf<DeviceId, PublicKey>()

    fun register(id: DeviceId, signingKey: PublicKey, agreementKey: PublicKey) {
        signing[id] = signingKey
        agreement[id] = agreementKey
    }

    override suspend fun signingPublicKeyOf(deviceId: DeviceId): PublicKey? = signing[deviceId]
    override suspend fun agreementPublicKeyOf(deviceId: DeviceId): PublicKey? = agreement[deviceId]
}

/** One simulated Sink device: real crypto, real [RoutingEngine], fake radio. */
class SimulatedNode(
    val displayName: String,
    network: SimulatedMeshNetwork,
    scope: CoroutineScope,
    directory: MutableIdentityDirectory,
    retryPolicy: RetryPolicy = RetryPolicy(),
    clock: () -> Long = { System.currentTimeMillis() },
    rateLimiter: PeerRateLimiter = PeerRateLimiter(),
) {
    private val signingKeyPair = SinkKeyPairs.generate()
    private val agreementKeyPair = SinkKeyPairs.generate()
    val deviceId: DeviceId = DeviceId(Fingerprint.of(signingKeyPair.public))

    val queue: MessageQueue = InMemoryMessageQueue()
    val transport = SimulatedTransport(deviceId, network)
    val transportManager = TransportManager(listOf(transport), scope = scope)
    val routingEngine = RoutingEngine(
        localIdentity = LocalIdentity(deviceId, signingKeyPair, agreementKeyPair),
        transportManager = transportManager,
        identityDirectory = directory,
        messageQueue = queue,
        scope = scope,
        retryPolicy = retryPolicy,
        clock = clock,
        rateLimiter = rateLimiter,
    )

    init {
        directory.register(deviceId, signingKeyPair.public, agreementKeyPair.public)
    }

    suspend fun start() {
        transportManager.start()
    }
}
