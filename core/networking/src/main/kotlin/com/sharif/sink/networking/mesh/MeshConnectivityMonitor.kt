package com.sharif.sink.networking.mesh

import com.sharif.sink.common.di.ApplicationScope
import com.sharif.sink.datastore.SinkPreferences
import com.sharif.sink.mesh.TransportManager
import com.sharif.sink.permissions.PermissionChecker
import com.sharif.sink.permissions.SinkPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "what is the local mesh doing right now," and the one place that
 * decides when a previously-failed local-mesh start is worth retrying automatically — the user
 * granting a permission or flipping "Nearby discovery" back on should visibly take effect without
 * needing to force-quit the app, not just update a label the next time something else happens to
 * recompute it.
 */
@Singleton
class MeshConnectivityMonitor @Inject constructor(
    private val permissionChecker: PermissionChecker,
    private val preferences: SinkPreferences,
    private val radioStatus: MeshRadioStatusReporter,
    private val transportManager: TransportManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _permissionRefresh = MutableStateFlow(0)
    private var lastKnownPermissionGranted = false

    init {
        // Retry the moment discovery is turned back on from Settings, not just at next resume.
        scope.launch {
            var lastEnabled: Boolean? = null
            preferences.settings.collect { settings ->
                if (settings.nearbyDiscoveryEnabled && lastEnabled == false) {
                    transportManager.retryLocalMesh()
                }
                lastEnabled = settings.nearbyDiscoveryEnabled
            }
        }
    }

    /**
     * Call from onResume and right after a permission-request result, so a newly granted
     * permission is retried and the UI updates immediately rather than waiting on an unrelated
     * recomposition to notice.
     */
    fun refresh() {
        val granted = permissionChecker.isGranted(SinkPermission.NEARBY_DEVICES)
        if (granted && !lastKnownPermissionGranted) {
            scope.launch { transportManager.retryLocalMesh() }
        }
        lastKnownPermissionGranted = granted
        _permissionRefresh.value++
    }

    /** Explicit user-initiated retry (e.g. a "Retry" button after [MeshConnectivityState.Unavailable]). */
    fun retryNow() {
        scope.launch { transportManager.retryLocalMesh() }
    }

    val state: StateFlow<MeshConnectivityState> = combine(
        preferences.settings,
        radioStatus.state,
        transportManager.networkStatus,
        _permissionRefresh,
    ) { settings, radio, _, _ ->
        val peerCount = transportManager.reachablePeers().size
        when {
            !permissionChecker.isGranted(SinkPermission.NEARBY_DEVICES) -> MeshConnectivityState.PermissionRequired
            !settings.nearbyDiscoveryEnabled -> MeshConnectivityState.DiscoveryDisabled
            radio.activity == MeshRadioActivity.FAILED ->
                MeshConnectivityState.Unavailable(radio.failureReason ?: "Couldn't start nearby discovery")
            peerCount > 0 -> MeshConnectivityState.Connected(peerCount)
            radio.activity == MeshRadioActivity.ACTIVE -> MeshConnectivityState.Scanning
            else -> MeshConnectivityState.Starting
        }
    }.stateIn(scope, SharingStarted.Eagerly, MeshConnectivityState.Starting)
}
