package com.sharif.sink.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharif.sink.database.dao.ConversationDao
import com.sharif.sink.database.entity.ConversationEntity
import com.sharif.sink.datastore.SinkPreferences
import com.sharif.sink.mesh.NetworkStatus
import com.sharif.sink.mesh.TransportManager
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
    val networkStatus: NetworkStatus = NetworkStatus.OFFLINE,
    val conversations: List<ConversationEntity> = emptyList(),
    val updateInfo: UpdateInfo? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    conversationDao: ConversationDao,
    transportManager: TransportManager,
    private val updateChecker: UpdateChecker,
    private val preferences: SinkPreferences,
) : ViewModel() {

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        transportManager.networkStatus,
        conversationDao.observeAll(),
        _updateInfo,
    ) { status, conversations, updateInfo ->
        HomeUiState(networkStatus = status, conversations = conversations, updateInfo = updateInfo)
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
}
