package com.sharif.sink.feature.contacts.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sharif.sink.feature.contacts.ContactsRoute

const val CONTACTS_ROUTE = "contacts"

fun NavGraphBuilder.contactsScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenDiscovery: () -> Unit,
) {
    composable(CONTACTS_ROUTE) {
        ContactsRoute(onBack = onBack, onOpenConversation = onOpenConversation, onOpenDiscovery = onOpenDiscovery)
    }
}
