package com.sharif.sink.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharif.sink.datastore.SinkPreferences
import com.sharif.sink.mesh.RoutingEngine
import com.sharif.sink.protocol.ConversationId
import com.sharif.sink.protocol.DeviceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    preferences: SinkPreferences,
    private val routingEngine: RoutingEngine,
) : ViewModel() {
    /** null while still loading — the nav host waits rather than guessing a start destination. */
    val onboardingCompleted: StateFlow<Boolean?> = preferences.settings
        .map { it.onboardingCompleted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Derives the same [ConversationId] RoutingEngine uses for a direct message, so navigating
     * to a peer works identically whether or not a conversation with them already exists.
     */
    fun conversationIdFor(peerDeviceId: String): String =
        ConversationId.forDirectMessage(routingEngine.localDeviceId, DeviceId(peerDeviceId)).value
}
