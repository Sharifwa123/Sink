package com.sharif.sink.feature.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import com.sharif.sink.networking.mesh.MeshConnectivityState

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
        onRetryMesh = viewModel::retryMesh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryScreen(
    uiState: DiscoveryUiState,
    onBack: () -> Unit,
    onOpenPeer: (String) -> Unit,
    onRequestNearbyPermission: () -> Unit,
    onRetryMesh: () -> Unit,
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
            if (uiState.peers.isNotEmpty()) {
                LazyColumn(contentPadding = PaddingValues(12.dp)) {
                    items(uiState.peers, key = { it.deviceId }) { peer ->
                        PeerRow(peer, onClick = { onOpenPeer(peer.deviceId) })
                    }
                }
            } else {
                EmptyState(
                    state = uiState.meshState,
                    onRequestNearbyPermission = onRequestNearbyPermission,
                    onRetryMesh = onRetryMesh,
                )
            }
        }
    }
}

/**
 * Mirrors the states shown on Home's status card (see feature:home's MeshStatusCard) so a user
 * gets the same honest, specific picture here — "nothing found yet" used to look identical
 * whether Sink was actively scanning, blocked on a missing permission, or had failed outright.
 */
@Composable
private fun EmptyState(
    state: MeshConnectivityState,
    onRequestNearbyPermission: () -> Unit,
    onRetryMesh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            MeshConnectivityState.PermissionRequired -> {
                Icon(Icons.Filled.BluetoothDisabled, contentDescription = null, modifier = Modifier.size(40.dp))
                SpacerSmall()
                Text("Bluetooth & Wi-Fi permission is required", style = MaterialTheme.typography.titleMedium)
                SpacerSmall()
                Text(
                    "Sink needs Bluetooth and Wi-Fi access to discover other Sink devices near you.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SpacerSmall()
                Button(onClick = onRequestNearbyPermission) { Text("Allow") }
            }
            MeshConnectivityState.DiscoveryDisabled -> {
                Icon(Icons.Filled.VisibilityOff, contentDescription = null, modifier = Modifier.size(40.dp))
                SpacerSmall()
                Text("Nearby discovery is off", style = MaterialTheme.typography.titleMedium)
                SpacerSmall()
                Text(
                    "Turn it on in Settings to find nearby Sink devices.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is MeshConnectivityState.Unavailable -> {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(40.dp))
                SpacerSmall()
                Text("Nearby discovery unavailable", style = MaterialTheme.typography.titleMedium)
                SpacerSmall()
                Text(state.reason, style = MaterialTheme.typography.bodyMedium)
                SpacerSmall()
                Button(onClick = onRetryMesh) { Text("Retry") }
            }
            MeshConnectivityState.Starting -> {
                CircularProgressIndicator()
                SpacerSmall()
                Text("Starting nearby discovery…", style = MaterialTheme.typography.titleMedium)
            }
            MeshConnectivityState.Scanning -> {
                CircularProgressIndicator()
                SpacerSmall()
                Text("Searching for nearby devices…", style = MaterialTheme.typography.titleMedium)
                SpacerSmall()
                Text(
                    "Keep Sink open on nearby devices too — they'll appear here as they're found.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is MeshConnectivityState.Connected -> {
                // Connected but the peers list is briefly empty right after a link forms, before
                // the handshake persists a contacts-table row — a transient sliver, not a bug.
                CircularProgressIndicator()
                SpacerSmall()
                Text("Connected — waiting for device details…", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SpacerSmall() = androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))

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
