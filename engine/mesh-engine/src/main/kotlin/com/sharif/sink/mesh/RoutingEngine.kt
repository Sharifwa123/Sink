package com.sharif.sink.mesh

import com.sharif.sink.crypto.EciesCipher
import com.sharif.sink.crypto.DecryptionFailedException
import com.sharif.sink.crypto.SealedEnvelope
import com.sharif.sink.crypto.Signatures
import com.sharif.sink.protocol.ConversationId
import com.sharif.sink.protocol.DeliveryState
import com.sharif.sink.protocol.DeviceId
import com.sharif.sink.protocol.EncryptionMetadata
import com.sharif.sink.protocol.Message
import com.sharif.sink.protocol.MessageId
import com.sharif.sink.protocol.PROTOCOL_VERSION
import com.sharif.sink.protocol.PacketType
import com.sharif.sink.protocol.SinkPacket
import com.sharif.sink.protocol.TransportKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** Retransmission schedule for a message that hasn't been ACKed yet. */
data class RetryPolicy(
    val baseDelayMillis: Long = 5_000,
    val multiplier: Double = 2.0,
    val maxDelayMillis: Long = 15L * 60 * 1000,
    val maxAttempts: Int = 8,
) {
    fun delayForAttempt(attempt: Int): Long {
        val raw = (baseDelayMillis * Math.pow(multiplier, (attempt - 1).coerceAtLeast(0).toDouble())).toLong()
        return raw.coerceAtMost(maxDelayMillis)
    }
}

sealed interface SendMessageOutcome {
    data class Accepted(val messageId: MessageId) : SendMessageOutcome
    data class Rejected(val reason: String) : SendMessageOutcome
}

private data class RetryBookkeeping(
    val packet: SinkPacket,
    val attempts: Int,
    val nextRetryAtEpochMillis: Long,
)

/** Sanity bound against a malicious/malformed oversized packet exhausting memory or bandwidth. */
private const val MAX_CIPHERTEXT_BYTES = 1 * 1024 * 1024

/**
 * The core store-and-forward mesh router. Contains zero Android
 * dependencies so it can be exercised by [com.sharif.sink.mesh.simulation]
 * in plain JVM tests, and driven by real [CommunicationTransport]s on
 * device without any behavioral difference.
 *
 * Routing strategy: **TTL/hop-bounded controlled flooding.** A node with a
 * packet not addressed to itself forwards a copy to every currently
 * reachable neighbor except the one it arrived from, until `maxHops` is
 * reached or the message expires. Duplicate delivery is suppressed by
 * [MessageIdCache]. This is deliberately simple — see docs/ROUTING.md for
 * why a topology-aware/shortest-path protocol is not justified for the
 * MVP (no stable addressing scheme exists yet across an intermittently
 * connected mesh, and flooding bounded by TTL/hop/dedup is a standard,
 * well-understood strategy for opportunistic/DTN-style networks).
 */
class RoutingEngine(
    private val localIdentity: LocalIdentity,
    private val transportManager: TransportManager,
    private val identityDirectory: IdentityDirectory,
    private val messageQueue: MessageQueue,
    private val scope: CoroutineScope,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val rateLimiter: PeerRateLimiter = PeerRateLimiter(),
    private val localDisplayName: () -> String = { "Sink User" },
) {
    val localDeviceId: DeviceId get() = localIdentity.deviceId

    private val seenPackets = MessageIdCache()
    private val retryState = HashMap<MessageId, RetryBookkeeping>()

    /**
     * Every DATA packet this device has originated, kept independent of [retryState]'s
     * lifecycle so a message that's exhausted its automatic retries (FAILED) can still be
     * explicitly resent via [sendViaSpecificTransport] — e.g. the user tapping "Send by SMS".
     * Cleared once a message reaches a terminal state that makes resending meaningless.
     */
    private val packetById = HashMap<MessageId, SinkPacket>()

    private val _incomingMessages = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<Message> = _incomingMessages

    /**
     * A peer's [DeviceIdentity], verified and emitted once its HELLO handshake succeeds.
     * This is trust-on-first-use: the identity is self-certifying (its own embedded signing
     * key both matches the claimed [DeviceId] and signs the handshake), not vouched for by
     * anyone else. See docs/SECURITY.md and docs/THREAT_MODEL.md for exactly what that does
     * and does not protect against, and the fingerprint-comparison flow that upgrades it to a
     * verified contact.
     */
    private val _discoveredIdentities = MutableSharedFlow<com.sharif.sink.protocol.DeviceIdentity>(extraBufferCapacity = 16)
    val discoveredIdentities: SharedFlow<com.sharif.sink.protocol.DeviceIdentity> = _discoveredIdentities

    init {
        transportManager.registerPacketListener { received ->
            handleReceivedPacket(received.packet, received.fromPeer, received.viaTransport)
        }
        transportManager.registerPeerConnectedListener { peer ->
            // A newly connected peer is new information a backoff timer can't anticipate:
            // any message that never had a route yet deserves an immediate attempt rather
            // than waiting for its next scheduled retry.
            retryQueuedMessagesNow()
            sendHello(peer)
        }
    }

    /** Announces this device's identity to a newly connected neighbor so messaging becomes possible. */
    private suspend fun sendHello(peer: DeviceId) {
        val identity = com.sharif.sink.protocol.DeviceIdentity(
            deviceId = localDeviceId,
            signingPublicKey = com.sharif.sink.crypto.SinkKeyPairs.encodePublicKey(localIdentity.signingKeyPair.public),
            agreementPublicKey = com.sharif.sink.crypto.SinkKeyPairs.encodePublicKey(localIdentity.agreementKeyPair.public),
            displayName = localDisplayName(),
        )
        val now = clock()
        val unsigned = SinkPacket(
            packetType = PacketType.HELLO,
            messageId = MessageId.new(),
            senderId = localDeviceId,
            destinationId = peer,
            conversationId = ConversationId.forDirectMessage(localDeviceId, peer),
            createdAtEpochMillis = now,
            ttlSeconds = 60,
            hopCount = 0,
            maxHops = 0, // HELLO is a direct-link announcement, never relayed
            ciphertext = com.sharif.sink.protocol.DeviceIdentityCodec.encode(identity),
            encryptionMetadata = EncryptionMetadata("NONE", ByteArray(0), ByteArray(0)),
            senderSignature = ByteArray(0),
        )
        val signed = unsigned.copy(
            senderSignature = Signatures.sign(localIdentity.signingKeyPair.private, PacketSigning.canonicalBytes(unsigned)),
        )
        transportManager.sendToPeer(peer, signed)
    }

    suspend fun sendMessage(
        recipientId: DeviceId,
        body: String,
        conversationId: ConversationId = ConversationId.forDirectMessage(localDeviceId, recipientId),
        ttlSeconds: Int = SinkPacket.DEFAULT_TTL_SECONDS,
        maxHops: Int = SinkPacket.DEFAULT_MAX_HOPS,
    ): SendMessageOutcome {
        val recipientKey = identityDirectory.agreementPublicKeyOf(recipientId)
            ?: return SendMessageOutcome.Rejected(
                "Sink doesn't have $recipientId's encryption key yet. Connect with this contact first.",
            )

        val now = clock()
        val messageId = MessageId.new()
        val message = Message(
            messageId = messageId,
            conversationId = conversationId,
            senderId = localDeviceId,
            recipientId = recipientId,
            body = body,
            createdAtEpochMillis = now,
            ttlSeconds = ttlSeconds,
            maxHops = maxHops,
            hopCount = 0,
            deliveryState = DeliveryState.QUEUED,
        )
        messageQueue.enqueue(message)

        val envelope = EciesCipher.encrypt(recipientKey, body.toByteArray(Charsets.UTF_8))
        val unsigned = SinkPacket(
            packetType = PacketType.DATA,
            messageId = messageId,
            senderId = localDeviceId,
            destinationId = recipientId,
            conversationId = conversationId,
            createdAtEpochMillis = now,
            ttlSeconds = ttlSeconds,
            hopCount = 0,
            maxHops = maxHops,
            ciphertext = envelope.ciphertext,
            encryptionMetadata = EncryptionMetadata(
                algorithm = EciesCipher.ALGORITHM_ID,
                senderEphemeralPublicKey = envelope.ephemeralPublicKey,
                nonce = envelope.nonce,
            ),
            senderSignature = ByteArray(0),
        )
        val signed = unsigned.copy(
            senderSignature = Signatures.sign(localIdentity.signingKeyPair.private, PacketSigning.canonicalBytes(unsigned)),
        )

        retryState[messageId] = RetryBookkeeping(signed, attempts = 0, nextRetryAtEpochMillis = now)
        packetById[messageId] = signed
        attemptSend(messageId)
        return SendMessageOutcome.Accepted(messageId)
    }

    /** Called by the app on a schedule (WorkManager) or connectivity change to drive retries. */
    suspend fun retrySweep() {
        val now = clock()
        val due = retryState.filterValues { it.nextRetryAtEpochMillis <= now }.keys.toList()
        due.forEach { attemptSend(it) }
    }

    /**
     * Explicitly resends a message over one named transport, bypassing the normal
     * ranked-broadcast path entirely. This is the only way SMS ever sends: never as part
     * of automatic flooding (see [TransportManager]'s `MESH_BROADCAST_KINDS`), only when the
     * user taps "Send by SMS" on a message that has no other route — including one already
     * marked FAILED, since the packet is kept (see [packetById]) specifically for this.
     */
    suspend fun sendViaSpecificTransport(messageId: MessageId, kind: TransportKind): SendMessageOutcome {
        val packet = packetById[messageId]
            ?: return SendMessageOutcome.Rejected("This message is no longer available to resend")
        val transport = transportManager.transportOfKind(kind)
            ?: return SendMessageOutcome.Rejected("$kind isn't available on this device")

        return when (val result = transport.send(packet.destinationId, packet)) {
            TransportSendResult.Delivered -> {
                messageQueue.updateState(messageId, DeliveryState.SENT_TO_PEER, kind)
                SendMessageOutcome.Accepted(messageId)
            }
            is TransportSendResult.Failed -> SendMessageOutcome.Rejected(result.reason)
        }
    }

    /** Every message id still awaiting ACK, for diagnostics. */
    fun pendingRetryCount(): Int = retryState.size

    /**
     * Attempts every message that's still [DeliveryState.QUEUED] (never yet handed to
     * anyone) immediately, ignoring its scheduled backoff time. Called when a peer just
     * became reachable — that's new information the backoff schedule couldn't have known
     * about. Messages already SENT_TO_PEER/RELAYING keep their normal backoff, since they
     * do have an outstanding attempt and don't need to be re-flooded on every connect event.
     */
    private suspend fun retryQueuedMessagesNow() {
        val queuedIds = retryState.keys.toList().filter { id ->
            messageQueue.get(id)?.deliveryState == DeliveryState.QUEUED
        }
        queuedIds.forEach { attemptSend(it) }
    }

    private suspend fun attemptSend(messageId: MessageId) {
        val bookkeeping = retryState[messageId] ?: return
        val now = clock()

        if (bookkeeping.packet.isExpired(now)) {
            retryState.remove(messageId)
            packetById.remove(messageId) // too stale to be worth an explicit resend either
            messageQueue.updateState(messageId, DeliveryState.EXPIRED)
            return
        }
        if (bookkeeping.attempts >= retryPolicy.maxAttempts) {
            retryState.remove(messageId)
            // packetById is intentionally kept here: FAILED is exactly when the user might
            // reach for "Send by SMS", so the packet must still be resendable.
            messageQueue.updateState(messageId, DeliveryState.FAILED)
            return
        }

        messageQueue.updateState(messageId, DeliveryState.SENDING)
        val deliveredTo = transportManager.broadcast(bookkeeping.packet)

        val nextAttempt = bookkeeping.attempts + 1
        retryState[messageId] = bookkeeping.copy(
            attempts = nextAttempt,
            nextRetryAtEpochMillis = now + retryPolicy.delayForAttempt(nextAttempt),
        )

        when {
            deliveredTo.isEmpty() -> messageQueue.updateState(messageId, DeliveryState.QUEUED)
            bookkeeping.packet.destinationId in deliveredTo -> messageQueue.updateState(messageId, DeliveryState.SENT_TO_PEER)
            else -> messageQueue.updateState(messageId, DeliveryState.RELAYING)
        }
    }

    private suspend fun handleReceivedPacket(packet: SinkPacket, fromPeer: DeviceId, fromTransportKind: TransportKind) {
        val now = clock()
        // Cheapest checks first, before any crypto or dedup bookkeeping: a flooding or
        // oversized-packet neighbor should cost this device as little as possible to reject.
        if (!rateLimiter.allow(fromPeer, now)) return
        if (packet.ciphertext.size > MAX_CIPHERTEXT_BYTES) return
        if (packet.protocolVersion != PROTOCOL_VERSION) return // unknown version: fail gracefully, drop
        if (packet.isExpired(now)) return

        val isNew = seenPackets.observeAndCheckIfNew(packet.messageId, now)
        if (!isNew) return // duplicate: never reprocess, never re-forward

        if (packet.destinationId == localDeviceId) {
            handleForUs(packet, fromTransportKind)
            return
        }

        if (packet.hopLimitReached()) return // TTL/hop budget exhausted: drop, do not forward
        scope.launch { transportManager.broadcast(packet.withNextHop(), excludePeer = fromPeer) }
    }

    private suspend fun handleForUs(packet: SinkPacket, fromTransportKind: TransportKind) {
        when (packet.packetType) {
            PacketType.ACK -> {
                val ackedId = packet.acknowledgedMessageId ?: return
                retryState.remove(ackedId)
                packetById.remove(ackedId)
                messageQueue.updateState(ackedId, DeliveryState.DELIVERED, fromTransportKind)
            }

            PacketType.DATA -> {
                val senderKey = identityDirectory.signingPublicKeyOf(packet.senderId) ?: return
                val authentic = Signatures.verify(senderKey, PacketSigning.canonicalBytes(packet), packet.senderSignature)
                if (!authentic) return // can't authenticate claimed sender: drop untrusted packet

                val plaintext = try {
                    EciesCipher.decrypt(
                        localIdentity.agreementKeyPair.private,
                        SealedEnvelope(
                            ephemeralPublicKey = packet.encryptionMetadata.senderEphemeralPublicKey,
                            nonce = packet.encryptionMetadata.nonce,
                            ciphertext = packet.ciphertext,
                        ),
                    )
                } catch (e: DecryptionFailedException) {
                    return
                }

                val message = Message(
                    messageId = packet.messageId,
                    conversationId = packet.conversationId,
                    senderId = packet.senderId,
                    recipientId = localDeviceId,
                    body = String(plaintext, Charsets.UTF_8),
                    createdAtEpochMillis = packet.createdAtEpochMillis,
                    ttlSeconds = packet.ttlSeconds,
                    maxHops = packet.maxHops,
                    hopCount = packet.hopCount,
                    deliveryState = DeliveryState.DELIVERED,
                    deliveredViaTransport = fromTransportKind,
                    relayedThroughHopCount = packet.hopCount,
                )
                messageQueue.enqueue(message)
                _incomingMessages.emit(message)
                sendAck(packet)
            }

            PacketType.HELLO -> handleHello(packet)
        }
    }

    /**
     * A HELLO carries the sender's whole [com.sharif.sink.protocol.DeviceIdentity] instead of
     * relying on [identityDirectory], because on first contact the directory has nothing to
     * look the sender up by yet — this is what populates it. Verification is therefore
     * self-contained: the embedded signing key must both (a) hash to the [DeviceId] this
     * packet claims to be from, and (b) have produced the packet's own signature. That proves
     * internal consistency (nobody else could have forged this exact claim), not that the
     * claim is who the human on the other end actually is — see docs/THREAT_MODEL.md.
     */
    private suspend fun handleHello(packet: SinkPacket) {
        val (identity, claimedKey) = try {
            val decodedIdentity = com.sharif.sink.protocol.DeviceIdentityCodec.decode(packet.ciphertext)
            val decodedKey = com.sharif.sink.crypto.SinkKeyPairs.decodePublicKey(decodedIdentity.signingPublicKey)
            decodedIdentity to decodedKey
        } catch (e: Exception) {
            return // malformed HELLO payload or key encoding: drop, never crash on untrusted input
        }

        val expectedDeviceId = DeviceId(com.sharif.sink.crypto.Fingerprint.of(claimedKey))
        if (expectedDeviceId != packet.senderId || expectedDeviceId != identity.deviceId) {
            return // the embedded key doesn't match the identity it claims to belong to
        }

        val authentic = Signatures.verify(claimedKey, PacketSigning.canonicalBytes(packet), packet.senderSignature)
        if (!authentic) return

        _discoveredIdentities.emit(identity)
    }

    private suspend fun sendAck(originalPacket: SinkPacket) {
        val now = clock()
        val unsigned = SinkPacket(
            packetType = PacketType.ACK,
            messageId = MessageId.new(),
            senderId = localDeviceId,
            destinationId = originalPacket.senderId,
            conversationId = originalPacket.conversationId,
            createdAtEpochMillis = now,
            ttlSeconds = SinkPacket.DEFAULT_TTL_SECONDS,
            hopCount = 0,
            maxHops = originalPacket.maxHops,
            ciphertext = ByteArray(0),
            encryptionMetadata = EncryptionMetadata("NONE", ByteArray(0), ByteArray(0)),
            senderSignature = ByteArray(0),
            acknowledgedMessageId = originalPacket.messageId,
        )
        val signed = unsigned.copy(
            senderSignature = Signatures.sign(localIdentity.signingKeyPair.private, PacketSigning.canonicalBytes(unsigned)),
        )
        transportManager.broadcast(signed)
    }
}
