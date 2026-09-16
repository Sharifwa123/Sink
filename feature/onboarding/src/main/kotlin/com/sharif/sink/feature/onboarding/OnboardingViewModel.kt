package com.sharif.sink.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharif.sink.crypto.android.LocalIdentityManager
import com.sharif.sink.datastore.SinkPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep {
    WELCOME,
    HOW_MESH_WORKS,
    DISPLAY_NAME,
    NEARBY_PERMISSION,
    SMS_FALLBACK_EXPLAINER,
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val displayName: String = "",
    val isCreatingIdentity: Boolean = false,
    val identityReady: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val identityManager: LocalIdentityManager,
    private val preferences: SinkPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onDisplayNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(displayName = name)
    }

    fun goTo(step: OnboardingStep) {
        _uiState.value = _uiState.value.copy(step = step)
    }

    /** Creates the device identity once the user has chosen a name — never regenerated after this. */
    fun confirmDisplayNameAndCreateIdentity(onDone: () -> Unit) {
        val name = _uiState.value.displayName.ifBlank { "Sink User" }
        _uiState.value = _uiState.value.copy(isCreatingIdentity = true)
        viewModelScope.launch {
            identityManager.getOrCreateIdentity(defaultDisplayName = name)
            identityManager.setDisplayName(name)
            _uiState.value = _uiState.value.copy(isCreatingIdentity = false, identityReady = true)
            onDone()
        }
    }

    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            preferences.setOnboardingCompleted(true)
            onDone()
        }
    }
}
