package com.sharif.sink.networking.transport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Base64
import com.sharif.sink.mesh.IncomingPacket
import com.sharif.sink.protocol.PacketCodec

/**
 * Registered in the manifest for `SMS_RECEIVED`. Only messages carrying
 * the [SMS_SINK_PREFIX] are treated as Sink traffic — everything else
 * (the user's ordinary texts) is left completely alone; this receiver
 * never reads or stores unrelated SMS content.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }
        if (!body.startsWith(SMS_SINK_PREFIX)) return

        val encoded = body.removePrefix(SMS_SINK_PREFIX)
        val packet = try {
            PacketCodec.decode(Base64.decode(encoded, Base64.NO_WRAP))
        } catch (e: Exception) {
            return // malformed/foreign payload wearing the Sink prefix: drop, don't crash
        }

        // The signature inside the packet is what actually authenticates senderId — this
        // transport-level label is only used for rate-limiting/bookkeeping, not trust.
        SmsInboundBus.tryEmit(IncomingPacket(fromPeer = packet.senderId, packet = packet))
    }
}
