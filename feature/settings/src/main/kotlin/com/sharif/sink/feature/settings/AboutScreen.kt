package com.sharif.sink.feature.settings

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AboutRoute(onBack: () -> Unit) {
    AboutScreen(onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Sink") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text("Sink", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Offline communication when connectivity is unavailable.",
                style = MaterialTheme.typography.bodyLarge,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
            Text("By SHARIF TECHNOLOGIES", style = MaterialTheme.typography.titleMedium)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
            Text(
                "Sink sends messages end-to-end encrypted, directly between nearby devices, " +
                    "and relays them through other Sink devices when the recipient isn't directly reachable. " +
                    "No account or phone number is required to use it.",
                style = MaterialTheme.typography.bodyMedium,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
            Text(
                "Privacy and security details are described in this project's SECURITY.md and " +
                    "THREAT_MODEL.md documentation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
