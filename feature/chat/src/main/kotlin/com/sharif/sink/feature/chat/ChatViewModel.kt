package com.sharif.sink.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharif.sink.database.dao.ContactDao
import com.sharif.sink.database.dao.ConversationDao
import com.sharif.sink.database.dao.MessageDao
import com.sharif.sink.database.entity.MessageEntity
import com.sharif.sink.datastore.SinkPreferences
import com.sharif.sink.feature.chat.navigation.decodeRouteArgument
import com.sharif.sink.mesh.RoutingEngine
import com.sharif.sink.mesh.SendMessageOutcome
import com.sharif.sink.protocol.ConversationId
import com.sharif.sink.protocol.DeviceId
import com.sharif.sink.protocol.MessageId
import com.sharif.sink.protocol.TransportKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val peerDisplayName: String = "",
    val draft: String = "",
    val messages: List<MessageEntity> = emptyList(),
    val sendError: String? = null,
    val smsFallbackEnabled: Boolean = true,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val contactDao: ContactDao,
    private val routingEngine: RoutingEngine,
    private val preferences: SinkPreferences,
) : ViewModel() {

    private val conversationId: String = decodeRouteArgument(checkNotNull(savedStateHandle["conversationId"]))
    private val peerId: DeviceId = DeviceId(decodeRouteArgument(checkNotNull(savedStateHandle["peerDeviceId"])))
    private val _draft = MutableStateFlow("")
    private val _sendError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ChatUiState> = combine(
        messageDao.observeConversation(conversationId),
        _draft,
        _sendError,
        // Observed, not a one-shot fetch: the peer id comes straight from the route (see
        // ChatNavigation), and the contact row may not exist yet the instant this screen opens
        // (right after connecting, before the HELLO handshake has finished persisting it) — a
        // one-shot lookup would freeze on the raw device id forever once that happened.
        contactDao.observe(peerId.value),
        preferences.settings,
    ) { messages, draft, error, contact, settings ->
        ChatUiState(
            peerDisplayName = contact?.displayName?.takeIf { it.isNotBlank() } ?: peerId.value,
            draft = draft,
            messages = messages,
            sendError = error,
            smsFallbackEnabled = settings.smsFallbackEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        viewModelScope.launch { conversationDao.clearUnread(conversationId) }
    }

    fun onDraftChanged(text: String) {
        _draft.value = text
    }

    fun sendMessage() {
        val body = _draft.value.trim()
        if (body.isEmpty()) return

        viewModelScope.launch {
            when (val outcome = routingEngine.sendMessage(peerId, body, ConversationId(conversationId))) {
                is SendMessageOutcome.Accepted -> {
                    _draft.value = ""
                    _sendError.value = null
                }
                is SendMessageOutcome.Rejected -> _sendError.value = outcome.reason
            }
        }
    }

    fun retryViaSms(messageId: String) {
        viewModelScope.launch {
            val outcome = routingEngine.sendViaSpecificTransport(MessageId(messageId), TransportKind.SMS)
            if (outcome is SendMessageOutcome.Rejected) {
                _sendError.value = outcome.reason
            }
        }
    }

    fun dismissError() {
        _sendError.value = null
    }
}
