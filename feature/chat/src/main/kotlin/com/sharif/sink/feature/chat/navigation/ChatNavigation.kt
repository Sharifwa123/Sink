package com.sharif.sink.feature.chat.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sharif.sink.feature.chat.ChatRoute
import java.net.URLDecoder
import java.net.URLEncoder

private const val CHAT_ROUTE_PATTERN = "chat/{conversationId}/{peerDeviceId}"
private const val ENCODING = "UTF-8"

/**
 * A [com.sharif.sink.protocol.DeviceId] fingerprint contains spaces, so it's URL-encoded going
 * into the route and decoded back out in [com.sharif.sink.feature.chat.ChatViewModel] — plain
 * Compose Navigation route segments are not automatically escaped.
 */
fun chatRoute(conversationId: String, peerDeviceId: String) =
    "chat/${URLEncoder.encode(conversationId, ENCODING)}/${URLEncoder.encode(peerDeviceId, ENCODING)}"

fun NavGraphBuilder.chatScreen(onBack: () -> Unit, onOpenMessageDetails: (String) -> Unit) {
    composable(
        route = CHAT_ROUTE_PATTERN,
        arguments = listOf(
            navArgument("conversationId") { type = NavType.StringType },
            navArgument("peerDeviceId") { type = NavType.StringType },
        ),
    ) {
        ChatRoute(onBack = onBack, onOpenMessageDetails = onOpenMessageDetails)
    }
}

internal fun decodeRouteArgument(value: String): String = URLDecoder.decode(value, ENCODING)
