package com.sharif.sink.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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

@Composable
fun DiagnosticsRoute(
    onBack: () -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    DiagnosticsScreen(uiState = uiState, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiagnosticsScreen(uiState: DiagnosticsUiState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            DiagnosticRow("Device ID", uiState.deviceId)
            DiagnosticRow("Protocol version", uiState.protocolVersion.toString())
            DiagnosticRow("Network status", uiState.networkStatus.name)
            DiagnosticRow("Directly connected peers", uiState.directlyConnectedPeers.toString())
            DiagnosticRow("Queued messages", uiState.queuedMessages.toString())
            DiagnosticRow("Messages awaiting ACK", uiState.pendingRetries.toString())

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(
                "Mesh radios",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Sink's local mesh runs over these radios directly — never mobile data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            uiState.meshRadioStatus.forEach { (label, granted) -> RadioStatusRow(label, granted) }
        }
    }
}

@Composable
private fun RadioStatusRow(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
            contentDescription = if (granted) "Granted" else "Not granted",
            tint = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
    }
}
