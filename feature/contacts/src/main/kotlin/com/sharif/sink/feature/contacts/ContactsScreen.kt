package com.sharif.sink.feature.contacts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sharif.sink.database.entity.ContactEntity

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
) {
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

            if (uiState.contacts.isEmpty()) {
                Text(
                    "No contacts yet. Discover a nearby Sink device to add one.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(uiState.contacts, key = { it.deviceId }) { contact ->
                        ContactRow(
                            contact = contact,
                            onClick = { onOpenConversation(contact.deviceId) },
                            onMarkVerified = { onMarkVerified(contact.deviceId) },
                            onToggleBlocked = { onSetBlocked(contact.deviceId, !contact.isBlocked) },
                        )
                    }
                }
            }
        }
    }
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
