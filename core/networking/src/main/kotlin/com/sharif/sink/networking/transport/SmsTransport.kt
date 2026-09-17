package com.sharif.sink.networking.transport

import android.telephony.SmsManager
import android.util.Base64
import com.sharif.sink.database.dao.ContactDao
import com.sharif.sink.logging.SinkLogger
import com.sharif.sink.mesh.CommunicationTransport
import com.sharif.sink.mesh.IncomingPacket
import com.sharif.sink.mesh.TransportCapabilities
import com.sharif.sink.mesh.TransportSendResult
import com.sharif.sink.permissions.PermissionChecker
import com.sharif.sink.permissions.SinkPermission
import com.sharif.sink.protocol.DeviceId
import com.sharif.sink.protocol.PacketCodec
import com.sharif.sink.protocol.SinkPacket
import com.sharif.sink.protocol.TransportKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "SmsTransport"

/**
 * The text-only, explicit-user-action fallback.
 * Deliberately excluded from [com.sharif.sink.mesh.TransportManager]'s
 * automatic flooding broadcast — see that class's `MESH_BROADCAST_KINDS` —
 * so this transport is only ever invoked directly, after the user chooses
 * "Send by SMS" on a message that has no other route. SMS has no
 * "connection" concept, so [connectedPeers] is always empty; a caller must
 * already know the destination's phone number (from a saved contact).
 */
class SmsTransport(
    private val contactDao: ContactDao,
    private val permissionChecker: PermissionChecker,
    private val logger: SinkLogger,
) : CommunicationTransport {

    override val kind: TransportKind = TransportKind.SMS
    override val capabilities: TransportCapabilities = TransportCapabilities.TEXT_ONLY_SMS
    override val connectedPeers: StateFlow<Set<DeviceId>> = MutableStateFlow(emptySet())
    override val incomingPackets: Flow<IncomingPacket> = SmsInboundBus.packets

    override suspend fun start() = Unit
    override suspend fun stop() = Unit

    override suspend fun send(peer: DeviceId, packet: SinkPacket): TransportSendResult {
        if (!permissionChecker.isGranted(SinkPermission.SEND_SMS)) {
            return TransportSendResult.Failed("SMS permission not granted")
        }

        val phoneNumber = contactDao.get(peer.value)?.phoneNumberForSms
            ?: return TransportSendResult.Failed("No phone number saved for this contact")

        val encodedPacket = SMS_SINK_PREFIX + Base64.encodeToString(PacketCodec.encode(packet), Base64.NO_WRAP)

        @Suppress("DEPRECATION") // getDefault() covers single-SIM devices; multi-SIM subscription
        // selection is a documented gap, see docs/ANDROID_LIMITATIONS.md.
        val smsManager = SmsManager.getDefault()
        val parts = smsManager.divideMessage(encodedPacket)

        if (parts.size > SMS_MAX_SEGMENTS) {
            return TransportSendResult.Failed(
                "This message is too large to send by SMS (would need ${parts.size} segments)",
            )
        }

        return try {
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            // Handed to the carrier radio — not proof of delivery. The mesh-level ACK (if the
            // recipient's Sink app also parses it back out of their inbound SMS) is what
            // eventually marks the message DELIVERED, same as every other transport.
            TransportSendResult.Delivered
        } catch (e: Exception) {
            logger.w(TAG, "SMS send failed: ${e.javaClass.simpleName}")
            TransportSendResult.Failed(e.message ?: "SMS send failed")
        }
    }
}
