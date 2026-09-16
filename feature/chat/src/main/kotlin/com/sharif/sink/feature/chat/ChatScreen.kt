package com.sharif.sink.feature.chat

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sharif.sink.database.entity.MessageEntity

@Composable
fun ChatRoute(
    onBack: () -> Unit,
    onOpenMessageDetails: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    ChatScreen(
        uiState = uiState,
        onBack = onBack,
        onDraftChanged = viewModel::onDraftChanged,
        onSend = viewModel::sendMessage,
        onRetryViaSms = viewModel::retryViaSms,
        onDismissError = viewModel::dismissError,
        onOpenMessageDetails = onOpenMessageDetails,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    uiState: ChatUiState,
    onBack: () -> Unit,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onRetryViaSms: (String) -> Unit,
    onDismissError: () -> Unit,
    onOpenMessageDetails: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.peerDisplayName.ifBlank { "Chat" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                reverseLayout = true,
            ) {
                items(uiState.messages.reversed(), key = { it.messageId }) { message ->
                    MessageBubble(
                        message,
                        smsFallbackEnabled = uiState.smsFallbackEnabled,
                        onRetryViaSms = { onRetryViaSms(message.messageId) },
                        onOpenDetails = { onOpenMessageDetails(message.messageId) },
                    )
                }
            }

            uiState.sendError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onDismissError) { Text("Dismiss") }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.draft,
                    onValueChange = onDraftChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                )
                IconButton(onClick = onSend) {
                    Icon(Icons.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessageEntity,
    smsFallbackEnabled: Boolean,
    onRetryViaSms: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val isOutgoing = message.isOutgoing
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        Column(horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start) {
            Card(
                onClick = onOpenDetails,
                colors = CardDefaults.cardColors(
                    containerColor = if (isOutgoing) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            ) {
                Text(message.body, modifier = Modifier.padding(12.dp))
            }
            if (isOutgoing) {
                DeliveryStateLabel(message)
                if (message.deliveryState == "FAILED" && smsFallbackEnabled) {
                    TextButton(onClick = onRetryViaSms) { Text("Send by SMS") }
                }
            }
        }
    }
}

@Composable
private fun DeliveryStateLabel(message: MessageEntity) {
    val label = when (message.deliveryState) {
        "QUEUED" -> "Queued"
        "SENDING" -> "Sending…"
        "SENT_TO_PEER" -> "Sent"
        "RELAYING" -> message.relayedThroughHopCount?.let { "Relaying through $it device${if (it == 1) "" else "s"}" } ?: "Relaying…"
        "DELIVERED" -> "Delivered" + (message.deliveredViaTransport?.let { if (it == "SMS") " (SMS)" else "" } ?: "")
        "READ" -> "Read"
        "FAILED" -> "Failed to send"
        "EXPIRED" -> "Expired before delivery"
        else -> message.deliveryState
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, end = 4.dp),
    )
}
