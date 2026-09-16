package com.sharif.sink.feature.conversations.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sharif.sink.feature.conversations.MessageDetailsRoute

private const val MESSAGE_DETAILS_PATTERN = "message-details/{messageId}"

fun messageDetailsRoute(messageId: String) = "message-details/$messageId"

fun NavGraphBuilder.messageDetailsScreen(onBack: () -> Unit) {
    composable(
        route = MESSAGE_DETAILS_PATTERN,
        arguments = listOf(navArgument("messageId") { type = NavType.StringType }),
    ) {
        MessageDetailsRoute(onBack = onBack)
    }
}
