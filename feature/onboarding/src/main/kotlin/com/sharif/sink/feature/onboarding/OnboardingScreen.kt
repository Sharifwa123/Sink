package com.sharif.sink.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * A single linear flow, not five separate nav destinations — the steps
 * are strictly sequential and never revisited independently, so one
 * route hosting internal step state is simpler than fragmenting the nav
 * graph for no benefit.
 */
@Composable
fun OnboardingRoute(
    nearbyPermissionGranted: Boolean,
    onRequestNearbyPermission: () -> Unit,
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    OnboardingScreen(
        uiState = uiState,
        nearbyPermissionGranted = nearbyPermissionGranted,
        onDisplayNameChanged = viewModel::onDisplayNameChanged,
        onNext = { next -> viewModel.goTo(next) },
        onConfirmDisplayName = { viewModel.confirmDisplayNameAndCreateIdentity { viewModel.goTo(OnboardingStep.NEARBY_PERMISSION) } },
        onRequestNearbyPermission = onRequestNearbyPermission,
        onFinish = { viewModel.completeOnboarding(onOnboardingComplete) },
    )
}

@Composable
private fun OnboardingScreen(
    uiState: OnboardingUiState,
    nearbyPermissionGranted: Boolean,
    onDisplayNameChanged: (String) -> Unit,
    onNext: (OnboardingStep) -> Unit,
    onConfirmDisplayName: () -> Unit,
    onRequestNearbyPermission: () -> Unit,
    onFinish: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            when (uiState.step) {
                OnboardingStep.WELCOME -> WelcomeStep(onNext = { onNext(OnboardingStep.HOW_MESH_WORKS) })
                OnboardingStep.HOW_MESH_WORKS -> HowMeshWorksStep(onNext = { onNext(OnboardingStep.DISPLAY_NAME) })
                OnboardingStep.DISPLAY_NAME -> DisplayNameStep(
                    displayName = uiState.displayName,
                    isCreating = uiState.isCreatingIdentity,
                    onDisplayNameChanged = onDisplayNameChanged,
                    onConfirm = onConfirmDisplayName,
                )
                OnboardingStep.NEARBY_PERMISSION -> NearbyPermissionStep(
                    granted = nearbyPermissionGranted,
                    onRequest = onRequestNearbyPermission,
                    onNext = { onNext(OnboardingStep.SMS_FALLBACK_EXPLAINER) },
                )
                OnboardingStep.SMS_FALLBACK_EXPLAINER -> SmsExplainerStep(onFinish = onFinish)
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Text("Sink", style = MaterialTheme.typography.displaySmall)
    Spacer(Modifier.height(12.dp))
    Text(
        "Communicate nearby, even when the internet is unavailable.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(32.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
}

@Composable
private fun HowMeshWorksStep(onNext: () -> Unit) {
    Text("How Sink reaches people", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    Text(
        "Sink can use nearby devices as communication links. Your message can travel " +
            "through other Sink devices, one connection at a time, until it reaches its destination — " +
            "even if you can't reach that person directly.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(32.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
}

@Composable
private fun DisplayNameStep(
    displayName: String,
    isCreating: Boolean,
    onDisplayNameChanged: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Text("What should people call you?", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    Text(
        "This name is shown to nearby Sink users. Sink doesn't require your phone number or an account.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = displayName,
        onValueChange = onDisplayNameChanged,
        label = { Text("Display name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(32.dp))
    Button(
        onClick = onConfirm,
        enabled = displayName.isNotBlank() && !isCreating,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (isCreating) "Setting up…" else "Continue")
    }
}

@Composable
private fun NearbyPermissionStep(granted: Boolean, onRequest: () -> Unit, onNext: () -> Unit) {
    Text("Find nearby Sink users", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    Text(
        "Sink uses Bluetooth and Wi-Fi directly to discover and connect to other Sink devices " +
            "near you, so messages can reach people even without internet or mobile data. Depending on " +
            "your device, you may see this as separate Bluetooth and Wi-Fi prompts.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(32.dp))
    if (granted) {
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    } else {
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text("Allow Bluetooth & Wi-Fi access") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
    }
}

@Composable
private fun SmsExplainerStep(onFinish: () -> Unit) {
    Text("When nothing else works", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    Text(
        "If no nearby Sink device or internet route is available, Sink can offer to send your " +
            "message as a plain SMS text instead — only after you choose to, and only text, never photos or files.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(32.dp))
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Start using Sink") }
}
