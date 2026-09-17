package com.sharif.sink.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AboutRoute(onBack: () -> Unit, onOpenSecurityDoc: () -> Unit, onOpenThreatModelDoc: () -> Unit) {
    AboutScreen(onBack = onBack, onOpenSecurityDoc = onOpenSecurityDoc, onOpenThreatModelDoc = onOpenThreatModelDoc)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(
    onBack: () -> Unit,
    onOpenSecurityDoc: () -> Unit,
    onOpenThreatModelDoc: () -> Unit,
) {
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Sink", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Offline communication when connectivity is unavailable.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
                Text("By SHARIF TECHNOLOGIES", style = MaterialTheme.typography.titleMedium)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 16.dp))
                Text(
                    "Sink sends messages end-to-end encrypted, directly between nearby devices over " +
                        "Bluetooth and Wi-Fi, and relays them through other Sink devices when the recipient " +
                        "isn't directly reachable. No account or phone number is required to use it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Security") },
                supportingContent = { Text("Cryptography, key management, what's protected") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSecurityDoc),
            )
            ListItem(
                headlineContent = { Text("Threat model") },
                supportingContent = { Text("What Sink defends against, and what it doesn't") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenThreatModelDoc),
            )
        }
    }
}
