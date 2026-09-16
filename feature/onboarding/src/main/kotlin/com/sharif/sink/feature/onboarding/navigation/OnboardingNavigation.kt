package com.sharif.sink.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sharif.sink.feature.onboarding.OnboardingRoute

const val ONBOARDING_ROUTE = "onboarding"

fun NavGraphBuilder.onboardingScreen(
    nearbyPermissionGranted: Boolean,
    onRequestNearbyPermission: () -> Unit,
    onOnboardingComplete: () -> Unit,
) {
    composable(ONBOARDING_ROUTE) {
        OnboardingRoute(
            nearbyPermissionGranted = nearbyPermissionGranted,
            onRequestNearbyPermission = onRequestNearbyPermission,
            onOnboardingComplete = onOnboardingComplete,
        )
    }
}
