package com.sharif.sink.feature.mesh.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sharif.sink.feature.mesh.MeshVisualizationRoute

const val MESH_VISUALIZATION_ROUTE = "mesh-visualization"

fun NavGraphBuilder.meshVisualizationScreen(onBack: () -> Unit) {
    composable(MESH_VISUALIZATION_ROUTE) {
        MeshVisualizationRoute(onBack = onBack)
    }
}
