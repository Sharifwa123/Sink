package com.sharif.sink.protocol

import java.util.UUID

/**
 * Globally unique message identifier. Never derived from a local database
 * auto-increment id — messages are created independently on multiple
 * devices and must be identifiable without coordination.
 */
@JvmInline
value class MessageId(val value: String) {
    companion object {
        fun new(): MessageId = MessageId(UUID.randomUUID().toString())
    }

    override fun toString(): String = value
}

/**
 * Stable identity of a Sink device. Derived from the device's public key
 * fingerprint, never from a phone number, so it can be used as a routing
 * address without revealing carrier-level identity.
 */
@JvmInline
value class DeviceId(val value: String) {
    override fun toString(): String = value
}

/**
 * Identifies a logical conversation (1:1 for MVP). Kept distinct from
 * [DeviceId] so future group conversations don't require a schema change.
 */
@JvmInline
value class ConversationId(val value: String) {
    companion object {
        fun forDirectMessage(a: DeviceId, b: DeviceId): ConversationId {
            val sorted = listOf(a.value, b.value).sorted()
            return ConversationId("dm:${sorted[0]}:${sorted[1]}")
        }
    }

    override fun toString(): String = value
}
