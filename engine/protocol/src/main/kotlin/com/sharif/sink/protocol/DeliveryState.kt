package com.sharif.sink.protocol

/**
 * Lifecycle state of an outgoing or incoming message.
 *
 * Deliberately distinguishes "accepted by the next relay" ([SENT_TO_PEER],
 * [RELAYING]) from "reached the final recipient" ([DELIVERED]) — a relay
 * accepting a packet is not delivery, and the UI must never conflate them.
 */
enum class DeliveryState {
    QUEUED,
    SENDING,
    SENT_TO_PEER,
    RELAYING,
    DELIVERED,
    READ,
    FAILED,
    EXPIRED;

    val isTerminal: Boolean
        get() = this == DELIVERED || this == READ || this == FAILED || this == EXPIRED

    val isOutgoingInFlight: Boolean
        get() = this == QUEUED || this == SENDING || this == SENT_TO_PEER || this == RELAYING
}
