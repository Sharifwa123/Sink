package com.sharif.sink.networking.transport

import com.sharif.sink.mesh.IncomingPacket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * [SmsReceiver] is a transient BroadcastReceiver instance created fresh by
 * the OS per broadcast, so it can't hold [SmsTransport]'s state directly.
 * This process-wide singleton is the bridge between them.
 */
object SmsInboundBus {
    private val _packets = MutableSharedFlow<IncomingPacket>(extraBufferCapacity = 16)
    val packets: SharedFlow<IncomingPacket> = _packets

    fun tryEmit(packet: IncomingPacket) {
        _packets.tryEmit(packet)
    }
}
