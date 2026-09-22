package com.sharif.sink.feature.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharif.sink.database.dao.PeerDao
import com.sharif.sink.database.entity.PeerEntity
import com.sharif.sink.networking.mesh.MeshConnectivityMonitor
import com.sharif.sink.networking.mesh.MeshConnectivityState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DiscoveryUiState(
    val meshState: MeshConnectivityState = MeshConnectivityState.Starting,
    val peers: List<PeerEntity> = emptyList(),
)

/**
 * Shows only real, previously-verified-by-handshake peers (see
 * PeerIdentityPersister) — never a fabricated distance/signal number.
 * Sink must not invent proximity data it can't actually measure, so this
 * screen intentionally has no "meters away" UI.
 */
@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    peerDao: PeerDao,
    private val meshConnectivityMonitor: MeshConnectivityMonitor,
) : ViewModel() {

    val uiState: StateFlow<DiscoveryUiState> = combine(
        meshConnectivityMonitor.state,
        peerDao.observeAll(),
    ) { meshState, peers ->
        DiscoveryUiState(meshState = meshState, peers = peers)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoveryUiState())

    fun retryMesh() {
        meshConnectivityMonitor.retryNow()
    }
}
