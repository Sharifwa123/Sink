package com.sharif.sink.networking.mesh

/**
 * The single, UI-facing answer to "what is the local mesh doing right now" — Home, Discovery,
 * and Diagnostics all render the same [MeshConnectivityMonitor.state] instead of each guessing
 * from raw permission/settings/transport state independently.
 */
sealed interface MeshConnectivityState {
    /** Bluetooth/Wi-Fi permission hasn't been granted — nothing can start until it is. */
    data object PermissionRequired : MeshConnectivityState

    /** The user turned "Nearby discovery" off in Settings. */
    data object DiscoveryDisabled : MeshConnectivityState

    /** Permission granted and discovery enabled, but the radio hasn't reported in yet. */
    data object Starting : MeshConnectivityState

    /** Actively advertising and discovering; no peer connected yet. */
    data object Scanning : MeshConnectivityState

    /** At least one nearby device is currently connected. */
    data class Connected(val peerCount: Int) : MeshConnectivityState

    /** Advertising/discovery itself failed to start — e.g. radios off, no Play Services. */
    data class Unavailable(val reason: String) : MeshConnectivityState
}
