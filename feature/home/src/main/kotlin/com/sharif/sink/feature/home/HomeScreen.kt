package com.sharif.sink.feature.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sharif.sink.database.entity.ConversationEntity
import com.sharif.sink.networking.mesh.MeshConnectivityState
import com.sharif.sink.networking.update.UpdateInfo

@Composable
fun HomeRoute(
    onOpenConversation: (String) -> Unit,
    onNewMessage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEducation: () -> Unit,
    onOpenMeshVisualization: () -> Unit,
    onRequestNearbyPermission: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    HomeScreen(
        uiState = uiState,
        onOpenConversation = onOpenConversation,
        onNewMessage = onNewMessage,
        onOpenSettings = onOpenSettings,
        onOpenEducation = onOpenEducation,
        onOpenMeshVisualization = onOpenMeshVisualization,
        onDismissUpdate = viewModel::dismissUpdate,
        onRequestNearbyPermission = onRequestNearbyPermission,
        onEnableNearbyDiscovery = viewModel::enableNearbyDiscovery,
        onRetryMesh = viewModel::retryMesh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onOpenConversation: (String) -> Unit,
    onNewMessage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEducation: () -> Unit,
    onOpenMeshVisualization: () -> Unit,
    onDismissUpdate: () -> Unit,
    onRequestNearbyPermission: () -> Unit,
    onEnableNearbyDiscovery: () -> Unit,
    onRetryMesh: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Sink") },
                navigationIcon = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Menu, contentDescription = "Settings")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenMeshVisualization) {
                        Icon(Icons.Filled.Hub, contentDescription = "Your mesh connections")
                    }
                    IconButton(onClick = onOpenEducation) {
                        Icon(Icons.Filled.School, contentDescription = "How Sink works")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewMessage) {
                Icon(Icons.Filled.Add, contentDescription = "New message")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            uiState.updateInfo?.let { UpdateAvailableBanner(it, onDismiss = onDismissUpdate) }

            MeshStatusCard(
                state = uiState.meshState,
                onRequestPermission = onRequestNearbyPermission,
                onEnableDiscovery = onEnableNearbyDiscovery,
                onRetry = onRetryMesh,
            )

            if (uiState.conversations.isEmpty()) {
                EmptyConversations(onNewMessage)
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(uiState.conversations, key = { it.conversationId }) { conversation ->
                        ConversationRow(conversation, onClick = { onOpenConversation(conversation.peerDeviceId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateAvailableBanner(updateInfo: UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Update available",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    "Sink ${updateInfo.latestVersion} is ready to download.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Button(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.downloadUrl)))
            }) { Text("Get it") }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/**
 * Home's headline answer to "is this thing working right now" — the app was reported as looking
 * "completely blind" with no indication of scanning, waiting, or connecting; this card always
 * shows an honest, specific, and where possible actionable state instead of a thin static line.
 */
@Composable
private fun MeshStatusCard(
    state: MeshConnectivityState,
    onRequestPermission: () -> Unit,
    onEnableDiscovery: () -> Unit,
    onRetry: () -> Unit,
) {
    val (containerColor, contentColor) = when (state) {
        is MeshConnectivityState.Connected -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        is MeshConnectivityState.Unavailable -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        MeshConnectivityState.PermissionRequired,
        MeshConnectivityState.DiscoveryDisabled,
        -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        MeshConnectivityState.Starting,
        MeshConnectivityState.Scanning,
        -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MeshStatusIcon(state)
            Spacer(Modifier.padding(horizontal = 6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(state.title(), style = MaterialTheme.typography.titleSmall)
                state.subtitle()?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            when (state) {
                MeshConnectivityState.PermissionRequired ->
                    TextButton(onClick = onRequestPermission) { Text("Enable") }
                MeshConnectivityState.DiscoveryDisabled ->
                    TextButton(onClick = onEnableDiscovery) { Text("Turn on") }
                is MeshConnectivityState.Unavailable ->
                    TextButton(onClick = onRetry) { Text("Retry") }
                else -> Unit
            }
        }
    }
}

@Composable
private fun MeshStatusIcon(state: MeshConnectivityState) {
    when (state) {
        MeshConnectivityState.Starting, MeshConnectivityState.Scanning ->
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        is MeshConnectivityState.Connected ->
            Icon(Icons.Filled.CheckCircle, contentDescription = null)
        MeshConnectivityState.PermissionRequired ->
            Icon(Icons.Filled.BluetoothDisabled, contentDescription = null)
        MeshConnectivityState.DiscoveryDisabled ->
            Icon(Icons.Filled.VisibilityOff, contentDescription = null)
        is MeshConnectivityState.Unavailable ->
            Icon(Icons.Filled.ErrorOutline, contentDescription = null)
    }
}

private fun MeshConnectivityState.title(): String = when (this) {
    MeshConnectivityState.Starting -> "Starting nearby discovery…"
    MeshConnectivityState.Scanning -> "Searching for nearby devices…"
    is MeshConnectivityState.Connected ->
        if (peerCount == 1) "Connected to 1 nearby device" else "Connected to $peerCount nearby devices"
    MeshConnectivityState.PermissionRequired -> "Bluetooth & Wi-Fi permission needed"
    MeshConnectivityState.DiscoveryDisabled -> "Nearby discovery is off"
    is MeshConnectivityState.Unavailable -> "Nearby discovery unavailable"
}

private fun MeshConnectivityState.subtitle(): String? = when (this) {
    MeshConnectivityState.Starting -> null
    MeshConnectivityState.Scanning -> "Keep Sink open on nearby devices too — they'll appear here."
    is MeshConnectivityState.Connected -> "Messages can relay through the mesh right now."
    MeshConnectivityState.PermissionRequired -> "Sink can't find nearby devices without it."
    MeshConnectivityState.DiscoveryDisabled -> "Turn it on to find and connect to nearby Sink devices."
    is MeshConnectivityState.Unavailable -> reason
}

@Composable
private fun EmptyConversations(onNewMessage: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No conversations yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.padding(top = 8.dp))
        Text(
            "Discover a nearby Sink device or start a new message to a known contact.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ConversationRow(conversation: ConversationEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(conversation.peerDisplayName, style = MaterialTheme.typography.titleMedium)
                conversation.lastMessagePreview?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (conversation.unreadCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = androidx.compose.foundation.shape.CircleShape,
                ) {
                    Text(
                        conversation.unreadCount.toString(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
