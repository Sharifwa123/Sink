package com.sharif.sink.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharif.sink.database.dao.ConversationDao
import com.sharif.sink.database.entity.ConversationEntity
import com.sharif.sink.datastore.SinkPreferences
import com.sharif.sink.networking.mesh.MeshConnectivityMonitor
import com.sharif.sink.networking.mesh.MeshConnectivityState
import com.sharif.sink.networking.update.UpdateChecker
import com.sharif.sink.networking.update.UpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val meshState: MeshConnectivityState = MeshConnectivityState.Starting,
    val conversations: List<ConversationEntity> = emptyList(),
    val updateInfo: UpdateInfo? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    conversationDao: ConversationDao,
    private val meshConnectivityMonitor: MeshConnectivityMonitor,
    private val updateChecker: UpdateChecker,
    private val preferences: SinkPreferences,
) : ViewModel() {

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        meshConnectivityMonitor.state,
        conversationDao.observeAll(),
        _updateInfo,
    ) { meshState, conversations, updateInfo ->
        HomeUiState(meshState = meshState, conversations = conversations, updateInfo = updateInfo)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            // A background/no-connectivity check failing silently is fine (see UpdateChecker);
            // there's simply nothing to show. This only ever reads GitHub's public releases API —
            // see docs/SECURITY.md and the "Check for updates" toggle in Settings.
            if (preferences.settings.first().updateCheckEnabled) {
                _updateInfo.value = updateChecker.checkForUpdate()
            }
        }
    }

    fun dismissUpdate() {
        _updateInfo.update { null }
    }

    /** "Nearby discovery is off" card action — flipping this retries the mesh automatically (see MeshConnectivityMonitor). */
    fun enableNearbyDiscovery() {
        viewModelScope.launch { preferences.setNearbyDiscoveryEnabled(true) }
    }

    /** "Unavailable" card action. */
    fun retryMesh() {
        meshConnectivityMonitor.retryNow()
    }
}
