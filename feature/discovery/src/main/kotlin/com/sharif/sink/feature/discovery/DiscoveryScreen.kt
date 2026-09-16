package com.sharif.sink.feature.discovery

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sharif.sink.database.entity.PeerEntity
import com.sharif.sink.mesh.NetworkStatus

@Composable
fun DiscoveryRoute(
    onBack: () -> Unit,
    onOpenPeer: (String) -> Unit,
    onRequestNearbyPermission: () -> Unit,
    viewModel: DiscoveryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    DiscoveryScreen(
        uiState = uiState,
        onBack = onBack,
        onOpenPeer = onOpenPeer,
        onRequestNearbyPermission = onRequestNearbyPermission,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryScreen(
    uiState: DiscoveryUiState,
    onBack: () -> Unit,
    onOpenPeer: (String) -> Unit,
    onRequestNearbyPermission: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nearby devices") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!uiState.nearbyPermissionGranted) {
                PermissionPrompt(onRequestNearbyPermission)
            } else if (uiState.peers.isEmpty()) {
                EmptyState(uiState.networkStatus)
            } else {
                LazyColumn(contentPadding = PaddingValues(12.dp)) {
                    items(uiState.peers, key = { it.deviceId }) { peer ->
                        PeerRow(peer, onClick = { onOpenPeer(peer.deviceId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text("Nearby device permission is required", style = MaterialTheme.typography.titleMedium)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
        Text(
            "Sink needs this permission to discover other Sink devices near you.",
            style = MaterialTheme.typography.bodyMedium,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
        Button(onClick = onRequest) { Text("Allow") }
    }
}

@Composable
private fun EmptyState(status: NetworkStatus) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text("No nearby Sink devices found", style = MaterialTheme.typography.titleMedium)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
        Text(
            if (status == NetworkStatus.OFFLINE) {
                "Keep Sink open and nearby devices will appear here as they're found."
            } else {
                "Connected, but no other Sink device has been discovered yet."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun PeerRow(peer: PeerEntity, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(peer.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${peer.connectionState.lowercase().replaceFirstChar { it.uppercase() }} • ${peer.transport}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
