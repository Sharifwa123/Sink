package com.sharif.sink.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharif.sink.database.dao.ConversationDao
import com.sharif.sink.database.entity.ConversationEntity
import com.sharif.sink.mesh.NetworkStatus
import com.sharif.sink.mesh.TransportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val networkStatus: NetworkStatus = NetworkStatus.OFFLINE,
    val conversations: List<ConversationEntity> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    conversationDao: ConversationDao,
    transportManager: TransportManager,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        transportManager.networkStatus,
        conversationDao.observeAll(),
    ) { status, conversations ->
        HomeUiState(networkStatus = status, conversations = conversations)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
