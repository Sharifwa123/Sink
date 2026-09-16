package com.sharif.sink.feature.conversations

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sharif.sink.database.entity.MessageEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun MessageDetailsRoute(
    onBack: () -> Unit,
    viewModel: MessageDetailsViewModel = hiltViewModel(),
) {
    val message by viewModel.message.collectAsState()
    MessageDetailsScreen(message = message, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageDetailsScreen(message: MessageEntity?, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Message details") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (message == null) {
                Text("Message not found", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            DetailRow("Message ID", message.messageId)
            DetailRow("Created", DateFormat.getDateTimeInstance().format(Date(message.createdAtEpochMillis)))
            DetailRow("Delivery state", message.deliveryState)
            DetailRow("Hops so far", message.hopCount.toString())
            DetailRow("Max hops allowed", message.maxHops.toString())
            message.relayedThroughHopCount?.let { DetailRow("Relayed through", "$it device${if (it == 1) "" else "s"}") }
            message.deliveredViaTransport?.let { DetailRow("Transport used", it) }
            DetailRow("Time-to-live", "${message.ttlSeconds}s")
            DetailRow("Encryption", "End-to-end (ECDH-P256 + AES-256-GCM)")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
    }
}
