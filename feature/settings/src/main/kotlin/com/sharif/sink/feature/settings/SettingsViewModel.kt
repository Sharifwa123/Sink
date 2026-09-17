package com.sharif.sink.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sharif.sink.crypto.android.LocalIdentityManager
import com.sharif.sink.datastore.SinkPreferences
import com.sharif.sink.datastore.SinkSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: SinkPreferences,
    private val identityManager: LocalIdentityManager,
) : ViewModel() {

    val settings: StateFlow<SinkSettings> = preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SinkSettings())

    fun displayName(): String = identityManager.displayName()

    fun setDisplayName(name: String) {
        if (name.isNotBlank()) identityManager.setDisplayName(name)
    }

    fun setNearbyDiscoveryEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setNearbyDiscoveryEnabled(enabled) }
    }

    fun setSmsFallbackEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setSmsFallbackEnabled(enabled) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setNotificationsEnabled(enabled) }
    }

    fun setUpdateCheckEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setUpdateCheckEnabled(enabled) }
    }
}
