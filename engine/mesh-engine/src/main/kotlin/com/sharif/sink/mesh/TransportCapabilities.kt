package com.sharif.sink.mesh

/**
 * What a transport can actually carry. The UI and the routing engine use
 * this to decide what's sendable right now instead of assuming every
 * transport can carry everything (it can't — SMS is text-only, mesh links
 * have small practical payload limits).
 */
data class TransportCapabilities(
    val supportsText: Boolean,
    val supportsImages: Boolean,
    val supportsAudio: Boolean,
    val supportsVideo: Boolean,
    val supportsFiles: Boolean,
    val maxPayloadSizeBytes: Int,
) {
    companion object {
        val TEXT_ONLY_SMS = TransportCapabilities(
            supportsText = true,
            supportsImages = false,
            supportsAudio = false,
            supportsVideo = false,
            supportsFiles = false,
            maxPayloadSizeBytes = 1200, // conservative bound across a few concatenated SMS segments
        )

        val LOCAL_MESH_TEXT = TransportCapabilities(
            supportsText = true,
            supportsImages = false,
            supportsAudio = false,
            supportsVideo = false,
            supportsFiles = false,
            maxPayloadSizeBytes = 32 * 1024,
        )

        val NEARBY_RICH = TransportCapabilities(
            supportsText = true,
            supportsImages = true,
            supportsAudio = true,
            supportsVideo = false,
            supportsFiles = true,
            maxPayloadSizeBytes = 10 * 1024 * 1024,
        )

        val INTERNET_RICH = TransportCapabilities(
            supportsText = true,
            supportsImages = true,
            supportsAudio = true,
            supportsVideo = true,
            supportsFiles = true,
            maxPayloadSizeBytes = 100 * 1024 * 1024,
        )
    }
}
