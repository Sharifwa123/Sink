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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sharif.sink.database.entity.ConversationEntity
import com.sharif.sink.mesh.NetworkStatus
import com.sharif.sink.networking.update.UpdateInfo

@Composable
fun HomeRoute(
    onOpenConversation: (String) -> Unit,
    onNewMessage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEducation: () -> Unit,
    onOpenMeshVisualization: () -> Unit,
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

            NetworkStatusBanner(uiState.networkStatus)

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

@Composable
private fun NetworkStatusBanner(status: NetworkStatus) {
    val (label, color) = when (status) {
        NetworkStatus.ONLINE -> "Online" to MaterialTheme.colorScheme.primary
        NetworkStatus.LOCAL_MESH -> "Connected — local mesh" to MaterialTheme.colorScheme.tertiary
        NetworkStatus.NEARBY -> "Connected — nearby device" to MaterialTheme.colorScheme.tertiary
        NetworkStatus.SMS_FALLBACK -> "SMS fallback available" to MaterialTheme.colorScheme.secondary
        NetworkStatus.OFFLINE -> "Offline — looking for nearby devices" to MaterialTheme.colorScheme.outline
    }
    Surface(color = color.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Surface(color = color, shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.size(8.dp)) {}
            Spacer(Modifier.padding(horizontal = 6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
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
