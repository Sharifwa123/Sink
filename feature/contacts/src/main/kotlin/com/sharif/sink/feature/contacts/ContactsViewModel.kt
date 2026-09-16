package com.sharif.sink.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharif.sink.crypto.android.LocalIdentityManager
import com.sharif.sink.database.dao.ContactDao
import com.sharif.sink.database.entity.ContactEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactsUiState(
    val contacts: List<ContactEntity> = emptyList(),
    val myFingerprint: String = "",
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val identityManager: LocalIdentityManager,
) : ViewModel() {

    private val _myFingerprint = MutableStateFlow("")

    val uiState: StateFlow<ContactsUiState> = combine(
        contactDao.observeAll(),
        _myFingerprint,
    ) { contacts, fingerprint ->
        ContactsUiState(contacts = contacts, myFingerprint = fingerprint)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    init {
        viewModelScope.launch { _myFingerprint.value = identityManager.fingerprint() }
    }

    fun markVerified(deviceId: String) {
        viewModelScope.launch { contactDao.markVerified(deviceId) }
    }

    fun setBlocked(deviceId: String, blocked: Boolean) {
        viewModelScope.launch { contactDao.setBlocked(deviceId, blocked) }
    }
}
