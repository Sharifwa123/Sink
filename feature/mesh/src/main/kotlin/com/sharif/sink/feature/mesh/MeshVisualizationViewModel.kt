package com.sharif.sink.feature.mesh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharif.sink.database.dao.PeerDao
import com.sharif.sink.database.entity.PeerEntity
import com.sharif.sink.mesh.PeerConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MeshVisualizationUiState(val connectedPeers: List<PeerEntity> = emptyList())

/**
 * Shows only this device's own directly-connected peers — real data from
 * [PeerDao], never a fabricated multi-hop topology. Sink doesn't currently
 * track the full mesh graph beyond one hop from each device's own vantage
 * point, so this view is honestly scoped to what's actually known here —
 * no fabricated nodes, consistent with the "How Sink Works" education
 * content (`feature/education`).
 */
@HiltViewModel
class MeshVisualizationViewModel @Inject constructor(
    peerDao: PeerDao,
) : ViewModel() {
    val uiState: StateFlow<MeshVisualizationUiState> = peerDao.observeAll()
        .map { peers -> MeshVisualizationUiState(peers.filter { it.connectionState == PeerConnectionState.CONNECTED.name }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeshVisualizationUiState())
}
