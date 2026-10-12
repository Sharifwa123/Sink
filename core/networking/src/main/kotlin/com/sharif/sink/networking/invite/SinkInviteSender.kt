package com.sharif.sink.networking.invite

import android.telephony.SmsManager
import com.sharif.sink.logging.SinkLogger
import com.sharif.sink.permissions.PermissionChecker
import com.sharif.sink.permissions.SinkPermission
import javax.inject.Inject

private const val TAG = "SinkInviteSender"
private const val RELEASES_URL = "https://github.com/sharifwa123/sink/releases/latest"

sealed interface InviteSendResult {
    data object Sent : InviteSendResult
    data class Failed(val reason: String) : InviteSendResult
}

/**
 * Sends a plain, human-readable SMS to someone who does not have Sink yet —
 * the WhatsApp-style "let's chat on Sink" invite. This is deliberately NOT
 * the `SINKv1:`-prefixed wire-protocol payload [com.sharif.sink.networking.transport.SmsTransport]
 * sends to an existing contact; a phone that has never run Sink can't parse
 * that, so this sends ordinary carrier SMS text instead. There's no way to
 * detect whether an arbitrary phone number has Sink installed in advance —
 * this is a one-way invite, not a connection.
 */
class SinkInviteSender @Inject constructor(
    private val permissionChecker: PermissionChecker,
    private val logger: SinkLogger,
) {
    fun sendInvite(phoneNumber: String, senderDisplayName: String): InviteSendResult {
        if (!permissionChecker.isGranted(SinkPermission.SEND_SMS)) {
            return InviteSendResult.Failed("SMS permission not granted")
        }

        val text = "$senderDisplayName wants to chat with you on Sink, a private offline-first " +
            "messenger. Get it here: $RELEASES_URL"

        @Suppress("DEPRECATION")
        val smsManager = SmsManager.getDefault()

        return try {
            val parts = smsManager.divideMessage(text)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            InviteSendResult.Sent
        } catch (e: Exception) {
            logger.w(TAG, "Invite SMS failed: ${e.javaClass.simpleName}")
            InviteSendResult.Failed(e.message ?: "SMS send failed")
        }
    }
}
