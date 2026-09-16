package com.sharif.sink.feature.conversations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharif.sink.database.dao.MessageDao
import com.sharif.sink.database.entity.MessageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessageDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    messageDao: MessageDao,
) : ViewModel() {

    private val _message = MutableStateFlow<MessageEntity?>(null)
    val message: StateFlow<MessageEntity?> = _message.asStateFlow()

    init {
        val messageId: String = checkNotNull(savedStateHandle["messageId"])
        viewModelScope.launch {
            _message.value = messageDao.get(messageId)
        }
    }
}
