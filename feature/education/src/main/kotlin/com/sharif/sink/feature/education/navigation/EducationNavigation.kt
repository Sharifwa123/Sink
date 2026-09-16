package com.sharif.sink.feature.education.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.sharif.sink.feature.education.EducationRoute

const val EDUCATION_ROUTE = "education"

fun NavGraphBuilder.educationScreen(onBack: () -> Unit) {
    composable(EDUCATION_ROUTE) {
        EducationRoute(onBack = onBack)
    }
}
