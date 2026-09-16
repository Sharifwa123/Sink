package com.sharif.sink.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharif.sink.database.dao.PeerDao
import com.sharif.sink.database.entity.PeerEntity
import com.sharif.sink.mesh.NetworkStatus
import com.sharif.sink.mesh.TransportManager
import com.sharif.sink.permissions.PermissionChecker
import com.sharif.sink.permissions.SinkPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DiscoveryUiState(
    val networkStatus: NetworkStatus = NetworkStatus.OFFLINE,
    val peers: List<PeerEntity> = emptyList(),
    val nearbyPermissionGranted: Boolean = false,
)

/**
 * Shows only real, previously-verified-by-handshake peers (see
 * PeerIdentityPersister) — never a fabricated distance/signal number.
 * The brief is explicit that Sink must not invent proximity data it can't
 * actually measure, so this screen intentionally has no "meters away" UI.
 */
@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    peerDao: PeerDao,
    transportManager: TransportManager,
    private val permissionChecker: PermissionChecker,
) : ViewModel() {

    private val _permissionRefresh = MutableStateFlow(0)

    val uiState: StateFlow<DiscoveryUiState> = combine(
        transportManager.networkStatus,
        peerDao.observeAll(),
        _permissionRefresh,
    ) { status, peers, _ ->
        DiscoveryUiState(
            networkStatus = status,
            peers = peers,
            nearbyPermissionGranted = permissionChecker.isGranted(SinkPermission.NEARBY_DEVICES),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoveryUiState())

    fun onResumed() {
        _permissionRefresh.value += 1
    }
}
