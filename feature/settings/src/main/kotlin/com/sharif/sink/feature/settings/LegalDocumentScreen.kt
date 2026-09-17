package com.sharif.sink.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * A legal/technical document (SECURITY.md, THREAT_MODEL.md) as it's actually
 * useful in the app: bundled as an asset (see app/build.gradle.kts'
 * copyLegalDocs task) so a user reads it without leaving Sink or needing to
 * find the GitHub repo, which most end users never will.
 */
enum class LegalDocument(val title: String, val assetFileName: String) {
    SECURITY("Security", "SECURITY.md"),
    THREAT_MODEL("Threat model", "THREAT_MODEL.md"),
}

@Composable
fun LegalDocumentRoute(document: LegalDocument, onBack: () -> Unit) {
    LegalDocumentScreen(document = document, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalDocumentScreen(document: LegalDocument, onBack: () -> Unit) {
    val context = LocalContext.current
    var lines by remember(document) { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(document) {
        lines = context.assets.open(document.assetFileName)
            .bufferedReader()
            .use { it.readText() }
            .lines()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val currentLines = lines
        if (currentLines == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            items(currentLines) { line -> MarkdownLine(line) }
        }
    }
}

/**
 * A deliberately minimal Markdown-ish renderer — headings and bullets, the
 * only structure these two docs actually use — rather than pulling in a full
 * Markdown library for two files.
 */
@Composable
private fun MarkdownLine(line: String) {
    val trimmed = line.trimEnd()
    when {
        trimmed.isBlank() -> Text("", modifier = Modifier.padding(vertical = 4.dp))
        trimmed.startsWith("### ") -> Text(
            trimmed.removePrefix("### "),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        trimmed.startsWith("## ") -> Text(
            trimmed.removePrefix("## "),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        trimmed.startsWith("# ") -> Text(
            trimmed.removePrefix("# "),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        trimmed == "---" -> Text("", modifier = Modifier.padding(vertical = 8.dp))
        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Text(
            "•  " + trimmed.removePrefix("- ").removePrefix("* ").replace("**", "").replace("`", ""),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
        )
        else -> Text(
            trimmed.replace("**", "").replace("`", ""),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}
