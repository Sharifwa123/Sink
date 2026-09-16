package com.sharif.sink.feature.discovery.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sharif.sink.feature.discovery.DiscoveryRoute

const val DISCOVERY_ROUTE = "discovery"

fun NavGraphBuilder.discoveryScreen(
    onBack: () -> Unit,
    onOpenPeer: (String) -> Unit,
    onRequestNearbyPermission: () -> Unit,
) {
    composable(DISCOVERY_ROUTE) {
        DiscoveryRoute(
            onBack = onBack,
            onOpenPeer = onOpenPeer,
            onRequestNearbyPermission = onRequestNearbyPermission,
        )
    }
}
