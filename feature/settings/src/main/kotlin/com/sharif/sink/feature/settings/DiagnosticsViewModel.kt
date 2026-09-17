package com.sharif.sink.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharif.sink.crypto.android.LocalIdentityManager
import com.sharif.sink.database.dao.MessageDao
import com.sharif.sink.mesh.NetworkStatus
import com.sharif.sink.mesh.RoutingEngine
import com.sharif.sink.mesh.TransportManager
import com.sharif.sink.permissions.PermissionChecker
import com.sharif.sink.protocol.PROTOCOL_VERSION
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiagnosticsUiState(
    val deviceId: String = "",
    val protocolVersion: Int = PROTOCOL_VERSION,
    val networkStatus: NetworkStatus = NetworkStatus.OFFLINE,
    val directlyConnectedPeers: Int = 0,
    val queuedMessages: Int = 0,
    val pendingRetries: Int = 0,
    val meshRadioStatus: List<Pair<String, Boolean>> = emptyList(),
)

/**
 * Never surfaces private key material or message plaintext — only
 * operational metadata useful for field testing.
 */
@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val identityManager: LocalIdentityManager,
    private val transportManager: TransportManager,
    private val routingEngine: RoutingEngine,
    private val messageDao: MessageDao,
    private val permissionChecker: PermissionChecker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val identity = identityManager.getOrCreateIdentity()
            _uiState.value = DiagnosticsUiState(
                deviceId = identity.deviceId.value,
                protocolVersion = PROTOCOL_VERSION,
                networkStatus = transportManager.networkStatus.value,
                directlyConnectedPeers = transportManager.reachablePeers().size,
                queuedMessages = messageDao.pendingForRetry().size,
                pendingRetries = routingEngine.pendingRetryCount(),
                meshRadioStatus = permissionChecker.meshRadioStatus(),
            )
        }
    }
}
