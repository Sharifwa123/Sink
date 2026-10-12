package com.sharif.sink.feature.contacts

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sharif.sink.database.entity.ContactEntity
import com.sharif.sink.database.entity.PendingInviteEntity

@Composable
fun ContactsRoute(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenDiscovery: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    ContactsScreen(
        uiState = uiState,
        onBack = onBack,
        onOpenConversation = onOpenConversation,
        onOpenDiscovery = onOpenDiscovery,
        onMarkVerified = viewModel::markVerified,
        onSetBlocked = viewModel::setBlocked,
        onInvite = viewModel::inviteByPhoneNumber,
        onResendInvite = viewModel::resendInvite,
        onDismissInviteError = viewModel::dismissInviteError,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactsScreen(
    uiState: ContactsUiState,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenDiscovery: () -> Unit,
    onMarkVerified: (String) -> Unit,
    onSetBlocked: (String, Boolean) -> Unit,
    onInvite: (String, String) -> Unit,
    onResendInvite: (PendingInviteEntity) -> Unit,
    onDismissInviteError: () -> Unit,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onOpenDiscovery) {
                        Icon(Icons.Filled.Search, contentDescription = "Find nearby devices")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add contact by phone number")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Your safety number", style = MaterialTheme.typography.labelMedium)
                    Text(uiState.myFingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Compare this with a contact's in person or over a trusted channel to verify their identity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (uiState.inviteError != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            uiState.inviteError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDismissInviteError) { Text("Dismiss") }
                    }
                }
            }

            if (uiState.contacts.isEmpty() && uiState.pendingInvites.isEmpty()) {
                Text(
                    "No contacts yet. Discover a nearby Sink device, or tap + to invite someone by phone number.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    items(uiState.contacts, key = { "contact-${it.deviceId}" }) { contact ->
                        ContactRow(
                            contact = contact,
                            onClick = { onOpenConversation(contact.deviceId) },
                            onMarkVerified = { onMarkVerified(contact.deviceId) },
                            onToggleBlocked = { onSetBlocked(contact.deviceId, !contact.isBlocked) },
                        )
                    }
                    items(uiState.pendingInvites, key = { "invite-${it.phoneNumber}" }) { invite ->
                        PendingInviteRow(invite = invite, onResend = { onResendInvite(invite) })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onInvite = { name, phone ->
                onInvite(name, phone)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onInvite: (String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var phoneNumber by rememberSaveable { mutableStateOf("") }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onInvite(name.trim(), phoneNumber.trim()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Not on Sink yet? They'll get a text inviting them to install it — " +
                        "just like inviting a friend to any other messaging app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone number") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && phoneNumber.isNotBlank(),
                onClick = { smsPermissionLauncher.launch(Manifest.permission.SEND_SMS) },
            ) { Text("Send invite via SMS") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ContactRow(
    contact: ContactEntity,
    onClick: () -> Unit,
    onMarkVerified: () -> Unit,
    onToggleBlocked: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(contact.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (contact.isVerified) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Verified",
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Text(
                contact.fingerprint,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                if (!contact.isVerified) {
                    TextButton(onClick = onMarkVerified) { Text("Mark verified") }
                }
                TextButton(onClick = onToggleBlocked) {
                    Text(if (contact.isBlocked) "Unblock" else "Block")
                }
            }
        }
    }
}

@Composable
private fun PendingInviteRow(invite: PendingInviteEntity, onResend: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(invite.displayName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "Invited",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                invite.phoneNumber,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Waiting for them to install Sink. You'll be able to chat here once they do.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onResend) { Text("Resend invite") }
        }
    }
}
