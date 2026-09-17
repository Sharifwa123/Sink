package com.sharif.sink.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sharif.sink.feature.settings.AboutRoute
import com.sharif.sink.feature.settings.DiagnosticsRoute
import com.sharif.sink.feature.settings.LegalDocument
import com.sharif.sink.feature.settings.LegalDocumentRoute
import com.sharif.sink.feature.settings.SettingsRoute

const val SETTINGS_ROUTE = "settings"
const val DIAGNOSTICS_ROUTE = "diagnostics"
const val ABOUT_ROUTE = "about"
const val SECURITY_DOC_ROUTE = "legal/security"
const val THREAT_MODEL_DOC_ROUTE = "legal/threat_model"

fun NavGraphBuilder.settingsScreen(
    onBack: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenEducation: () -> Unit,
) {
    composable(SETTINGS_ROUTE) {
        SettingsRoute(
            onBack = onBack,
            onOpenContacts = onOpenContacts,
            onOpenDiagnostics = onOpenDiagnostics,
            onOpenAbout = onOpenAbout,
            onOpenEducation = onOpenEducation,
        )
    }
}

fun NavGraphBuilder.diagnosticsScreen(onBack: () -> Unit) {
    composable(DIAGNOSTICS_ROUTE) {
        DiagnosticsRoute(onBack = onBack)
    }
}

fun NavGraphBuilder.aboutScreen(
    onBack: () -> Unit,
    onOpenSecurityDoc: () -> Unit,
    onOpenThreatModelDoc: () -> Unit,
) {
    composable(ABOUT_ROUTE) {
        AboutRoute(onBack = onBack, onOpenSecurityDoc = onOpenSecurityDoc, onOpenThreatModelDoc = onOpenThreatModelDoc)
    }
}

fun NavGraphBuilder.securityDocScreen(onBack: () -> Unit) {
    composable(SECURITY_DOC_ROUTE) {
        LegalDocumentRoute(document = LegalDocument.SECURITY, onBack = onBack)
    }
}

fun NavGraphBuilder.threatModelDocScreen(onBack: () -> Unit) {
    composable(THREAT_MODEL_DOC_ROUTE) {
        LegalDocumentRoute(document = LegalDocument.THREAT_MODEL, onBack = onBack)
    }
}
