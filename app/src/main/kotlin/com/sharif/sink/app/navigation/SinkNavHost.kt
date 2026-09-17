package com.sharif.sink.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.sharif.sink.app.AppViewModel
import com.sharif.sink.feature.chat.navigation.chatRoute
import com.sharif.sink.feature.chat.navigation.chatScreen
import com.sharif.sink.feature.contacts.navigation.contactsScreen
import com.sharif.sink.feature.conversations.navigation.messageDetailsRoute
import com.sharif.sink.feature.conversations.navigation.messageDetailsScreen
import com.sharif.sink.feature.discovery.navigation.discoveryScreen
import com.sharif.sink.feature.education.navigation.educationScreen
import com.sharif.sink.feature.home.navigation.HOME_ROUTE
import com.sharif.sink.feature.home.navigation.homeScreen
import com.sharif.sink.feature.mesh.navigation.meshVisualizationScreen
import com.sharif.sink.feature.onboarding.navigation.ONBOARDING_ROUTE
import com.sharif.sink.feature.onboarding.navigation.onboardingScreen
import com.sharif.sink.feature.settings.navigation.aboutScreen
import com.sharif.sink.feature.settings.navigation.diagnosticsScreen
import com.sharif.sink.feature.settings.navigation.securityDocScreen
import com.sharif.sink.feature.settings.navigation.threatModelDocScreen
import com.sharif.sink.feature.settings.navigation.settingsScreen
import com.sharif.sink.feature.contacts.navigation.CONTACTS_ROUTE
import com.sharif.sink.feature.discovery.navigation.DISCOVERY_ROUTE
import com.sharif.sink.feature.settings.navigation.ABOUT_ROUTE
import com.sharif.sink.feature.settings.navigation.DIAGNOSTICS_ROUTE
import com.sharif.sink.feature.settings.navigation.SECURITY_DOC_ROUTE
import com.sharif.sink.feature.settings.navigation.THREAT_MODEL_DOC_ROUTE
import com.sharif.sink.feature.settings.navigation.SETTINGS_ROUTE
import com.sharif.sink.feature.education.navigation.EDUCATION_ROUTE
import com.sharif.sink.feature.mesh.navigation.MESH_VISUALIZATION_ROUTE

@Composable
fun SinkNavHost(
    nearbyPermissionGranted: Boolean,
    onRequestNearbyPermission: () -> Unit,
    appViewModel: AppViewModel = hiltViewModel(),
) {
    val onboardingCompleted by appViewModel.onboardingCompleted.collectAsState()
    val completed = onboardingCompleted

    // Wait for the real answer instead of guessing a start destination — see AppViewModel.
    if (completed == null) {
        Box(modifier = Modifier.fillMaxSize()) {}
        return
    }

    val navController = rememberNavController()
    val startDestination = if (completed) HOME_ROUTE else ONBOARDING_ROUTE

    fun openConversation(peerDeviceId: String) {
        val conversationId = appViewModel.conversationIdFor(peerDeviceId)
        navController.navigate(chatRoute(conversationId, peerDeviceId))
    }

    NavHost(navController = navController, startDestination = startDestination) {
        onboardingScreen(
            nearbyPermissionGranted = nearbyPermissionGranted,
            onRequestNearbyPermission = onRequestNearbyPermission,
            onOnboardingComplete = {
                navController.navigate(HOME_ROUTE) {
                    popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                }
            },
        )

        homeScreen(
            onOpenConversation = ::openConversation,
            onNewMessage = { navController.navigate(CONTACTS_ROUTE) },
            onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
            onOpenEducation = { navController.navigate(EDUCATION_ROUTE) },
            onOpenMeshVisualization = { navController.navigate(MESH_VISUALIZATION_ROUTE) },
        )

        discoveryScreen(
            onBack = navController::popBackStack,
            onOpenPeer = ::openConversation,
            onRequestNearbyPermission = onRequestNearbyPermission,
        )

        contactsScreen(
            onBack = navController::popBackStack,
            onOpenConversation = ::openConversation,
            onOpenDiscovery = { navController.navigate(DISCOVERY_ROUTE) },
        )

        chatScreen(
            onBack = navController::popBackStack,
            onOpenMessageDetails = { messageId -> navController.navigate(messageDetailsRoute(messageId)) },
        )

        messageDetailsScreen(onBack = navController::popBackStack)

        meshVisualizationScreen(onBack = navController::popBackStack)

        educationScreen(onBack = navController::popBackStack)

        settingsScreen(
            onBack = navController::popBackStack,
            onOpenContacts = { navController.navigate(CONTACTS_ROUTE) },
            onOpenDiagnostics = { navController.navigate(DIAGNOSTICS_ROUTE) },
            onOpenAbout = { navController.navigate(ABOUT_ROUTE) },
            onOpenEducation = { navController.navigate(EDUCATION_ROUTE) },
        )

        diagnosticsScreen(onBack = navController::popBackStack)

        aboutScreen(
            onBack = navController::popBackStack,
            onOpenSecurityDoc = { navController.navigate(SECURITY_DOC_ROUTE) },
            onOpenThreatModelDoc = { navController.navigate(THREAT_MODEL_DOC_ROUTE) },
        )

        securityDocScreen(onBack = navController::popBackStack)

        threatModelDocScreen(onBack = navController::popBackStack)
    }
}
