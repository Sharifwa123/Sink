package com.sharif.sink.networking.transport

/** Marks an SMS body as a Sink protocol message rather than an ordinary text — see docs/NETWORK_PROTOCOL.md. */
internal const val SMS_SINK_PREFIX = "SINKv1:"

/** Sink refuses to fragment a message across more SMS segments than this — see docs/SECURITY.md. */
internal const val SMS_MAX_SEGMENTS = 6
