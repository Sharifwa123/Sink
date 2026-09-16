package com.sharif.sink.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sharif.sink.datastore.SinkSettings

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenEducation: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()
    SettingsScreen(
        settings = settings,
        displayName = viewModel.displayName(),
        onBack = onBack,
        onDisplayNameChanged = viewModel::setDisplayName,
        onNearbyToggle = viewModel::setNearbyDiscoveryEnabled,
        onSmsToggle = viewModel::setSmsFallbackEnabled,
        onNotificationsToggle = viewModel::setNotificationsEnabled,
        onOpenContacts = onOpenContacts,
        onOpenDiagnostics = onOpenDiagnostics,
        onOpenAbout = onOpenAbout,
        onOpenEducation = onOpenEducation,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    settings: SinkSettings,
    displayName: String,
    onBack: () -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onNearbyToggle: (Boolean) -> Unit,
    onSmsToggle: (Boolean) -> Unit,
    onNotificationsToggle: (Boolean) -> Unit,
    onOpenContacts: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenEducation: () -> Unit,
) {
    var name by remember(displayName) { mutableStateOf(displayName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Display name", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            onDisplayNameChanged(it)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider()
            }

            item {
                ListItem(
                    headlineContent = { Text("Nearby discovery") },
                    supportingContent = { Text("Let Sink discover and connect to nearby devices") },
                    trailingContent = { Switch(checked = settings.nearbyDiscoveryEnabled, onCheckedChange = onNearbyToggle) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("SMS fallback") },
                    supportingContent = { Text("Offer to send by SMS when no other route exists") },
                    trailingContent = { Switch(checked = settings.smsFallbackEnabled, onCheckedChange = onSmsToggle) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Notifications") },
                    trailingContent = { Switch(checked = settings.notificationsEnabled, onCheckedChange = onNotificationsToggle) },
                )
            }
            item { HorizontalDivider() }

            item {
                ListItem(
                    headlineContent = { Text("Blocked & known contacts") },
                    modifier = Modifier.clickableItem(onOpenContacts),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Network diagnostics") },
                    modifier = Modifier.clickableItem(onOpenDiagnostics),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("How Sink works") },
                    modifier = Modifier.clickableItem(onOpenEducation),
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("About Sink") },
                    modifier = Modifier.clickableItem(onOpenAbout),
                )
            }
        }
    }
}

private fun Modifier.clickableItem(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
