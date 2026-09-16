package com.sharif.sink.feature.home.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sharif.sink.feature.home.HomeRoute

const val HOME_ROUTE = "home"

fun NavGraphBuilder.homeScreen(
    onOpenConversation: (String) -> Unit,
    onNewMessage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEducation: () -> Unit,
    onOpenMeshVisualization: () -> Unit,
) {
    composable(HOME_ROUTE) {
        HomeRoute(
            onOpenConversation = onOpenConversation,
            onNewMessage = onNewMessage,
            onOpenSettings = onOpenSettings,
            onOpenEducation = onOpenEducation,
            onOpenMeshVisualization = onOpenMeshVisualization,
        )
    }
}
