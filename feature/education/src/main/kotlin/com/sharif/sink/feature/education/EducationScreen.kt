package com.sharif.sink.feature.education

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private data class Topic(val title: String, val body: String)

private val TOPICS = listOf(
    Topic(
        "What is a network?",
        "A network is just a way for devices to pass information to each other. The internet is " +
            "one huge network of networks. When it's unavailable, smaller local networks — like the " +
            "one Sink builds between nearby phones — can still work.",
    ),
    Topic(
        "What is peer-to-peer communication?",
        "Instead of every message going through a distant company's server, peer-to-peer means " +
            "devices talk directly to each other. Sink connects directly to other Sink devices near you.",
    ),
    Topic(
        "What is mesh topology?",
        "In a mesh, devices aren't just connected in a single line — many devices can connect to " +
            "several others at once. That means there's often more than one path for a message to travel.",
    ),
    Topic(
        "What is a relay node?",
        "A relay is a device that passes your message along without being the final recipient. " +
            "Any Sink device — including yours — can act as sender, receiver, or relay.",
    ),
    Topic(
        "How does a message actually travel?",
        "A wants to reach D, but can't connect to D directly.\n\n" +
            "    A → B → C → D\n\n" +
            "B and C relay the message — encrypted the whole way — until it reaches D. Neither B nor C " +
            "can read what A sent to D; they only see where it's headed and how long it has left to get there.",
    ),
    Topic(
        "What happens when one route disappears?",
        "If a relay device (like B) turns off or moves out of range mid-route, Sink doesn't lose the " +
            "message. It's stored on the sender's device and retried automatically once a new route — " +
            "possibly through a different relay — becomes available.",
    ),
    Topic(
        "What is store-and-forward?",
        "\"Store-and-forward\" means a device holds onto a message it can't yet deliver, and tries " +
            "again later instead of giving up. This is what lets Sink work even when connectivity comes " +
            "and goes.",
    ),
    Topic(
        "Why can Sink work without internet?",
        "Because messages don't have to go through a distant server — they travel directly between " +
            "nearby Sink devices. As long as there's a chain of Sink users between you and your recipient, " +
            "internet access isn't required.",
    ),
    Topic(
        "What does SMS fallback mean?",
        "If no nearby Sink device or internet route is available, Sink can offer to send your message " +
            "as a plain SMS text instead — only after you choose to, and only for text. It's the last resort, not the default.",
    ),
)

@Composable
fun EducationRoute(onBack: () -> Unit) {
    EducationScreen(onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EducationScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How Sink works") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            items(TOPICS) { topic ->
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(topic.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        topic.body,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = if (topic.body.contains("→")) FontFamily.Monospace else FontFamily.Default,
                    )
                }
            }
        }
    }
}
