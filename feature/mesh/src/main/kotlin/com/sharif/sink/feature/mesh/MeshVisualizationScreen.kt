package com.sharif.sink.feature.mesh

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MeshVisualizationRoute(
    onBack: () -> Unit,
    viewModel: MeshVisualizationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    MeshVisualizationScreen(peerCount = uiState.connectedPeers.size, peerNames = uiState.connectedPeers.map { it.displayName }, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeshVisualizationScreen(peerCount: Int, peerNames: List<String>, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your mesh connections") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                if (peerCount == 0) {
                    "You're not directly connected to any Sink device right now."
                } else {
                    "You're directly connected to $peerCount nearby device${if (peerCount == 1) "" else "s"}. " +
                        "Messages to devices beyond these can still travel through them as relays."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                MeshDiagram(peerNames)
            }
        }
    }
}

@Composable
private fun MeshDiagram(peerNames: List<String>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 * 0.7f

        // "You" at the center.
        drawCircle(color = androidx.compose.ui.graphics.Color(0xFF2DD4BF), radius = 28f, center = center)

        if (peerNames.isEmpty()) return@Canvas

        val angleStep = (2 * Math.PI / peerNames.size)
        peerNames.forEachIndexed { index, _ ->
            val angle = angleStep * index - Math.PI / 2
            val peerCenter = Offset(
                x = center.x + (radius * cos(angle)).toFloat(),
                y = center.y + (radius * sin(angle)).toFloat(),
            )
            drawLine(
                color = androidx.compose.ui.graphics.Color.Gray,
                start = center,
                end = peerCenter,
                strokeWidth = 3f,
            )
            drawCircle(color = androidx.compose.ui.graphics.Color(0xFF64748B), radius = 20f, center = peerCenter)
        }
    }
}
