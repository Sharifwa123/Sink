package com.sharif.sink.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharif.sink.crypto.android.LocalIdentityManager
import com.sharif.sink.database.dao.ContactDao
import com.sharif.sink.database.dao.PendingInviteDao
import com.sharif.sink.database.entity.ContactEntity
import com.sharif.sink.database.entity.PendingInviteEntity
import com.sharif.sink.networking.invite.InviteSendResult
import com.sharif.sink.networking.invite.SinkInviteSender
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
    val pendingInvites: List<PendingInviteEntity> = emptyList(),
    val myFingerprint: String = "",
    val inviteError: String? = null,
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val pendingInviteDao: PendingInviteDao,
    private val identityManager: LocalIdentityManager,
    private val inviteSender: SinkInviteSender,
) : ViewModel() {

    private val _myFingerprint = MutableStateFlow("")
    private val _inviteError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ContactsUiState> = combine(
        contactDao.observeAll(),
        pendingInviteDao.observeAll(),
        _myFingerprint,
        _inviteError,
    ) { contacts, pendingInvites, fingerprint, inviteError ->
        ContactsUiState(
            contacts = contacts,
            // A phone number that already belongs to a real contact isn't still "pending" —
            // drop it instead of showing the same person twice.
            pendingInvites = pendingInvites.filter { invite ->
                contacts.none { it.phoneNumberForSms == invite.phoneNumber }
            },
            myFingerprint = fingerprint,
            inviteError = inviteError,
        )
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

    /**
     * Adds (or re-invites) a contact by phone number who doesn't have a known Sink identity
     * yet. Sends a plain SMS invite rather than pretending a mesh connection exists — see
     * [SinkInviteSender].
     */
    fun inviteByPhoneNumber(displayName: String, phoneNumber: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val senderName = identityManager.displayName().ifBlank { "A Sink user" }
            when (val result = inviteSender.sendInvite(phoneNumber, senderName)) {
                is InviteSendResult.Sent -> {
                    pendingInviteDao.upsert(
                        PendingInviteEntity(
                            phoneNumber = phoneNumber,
                            displayName = displayName,
                            invitedAtEpochMillis = now,
                            lastInviteSentEpochMillis = now,
                        ),
                    )
                    _inviteError.value = null
                }
                is InviteSendResult.Failed -> _inviteError.value = result.reason
            }
        }
    }

    fun resendInvite(invite: PendingInviteEntity) {
        viewModelScope.launch {
            val senderName = identityManager.displayName().ifBlank { "A Sink user" }
            when (val result = inviteSender.sendInvite(invite.phoneNumber, senderName)) {
                is InviteSendResult.Sent -> {
                    pendingInviteDao.markInviteResent(invite.phoneNumber, System.currentTimeMillis())
                    _inviteError.value = null
                }
                is InviteSendResult.Failed -> _inviteError.value = result.reason
            }
        }
    }

    fun dismissInviteError() {
        _inviteError.value = null
    }
}
