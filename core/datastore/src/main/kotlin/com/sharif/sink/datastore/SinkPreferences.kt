package com.sharif.sink.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sink_settings")

data class SinkSettings(
    val onboardingCompleted: Boolean = false,
    val nearbyDiscoveryEnabled: Boolean = true,
    val smsFallbackEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val transportPriorityCsv: String = DEFAULT_TRANSPORT_PRIORITY,
) {
    companion object {
        const val DEFAULT_TRANSPORT_PRIORITY = "LOCAL_MESH,NEARBY_DIRECT,INTERNET,SMS"
    }
}

/**
 * Small, non-sensitive app settings. Identity/key material never lives
 * here — that's core:crypto's job, backed by Android Keystore.
 */
@Singleton
class SinkPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val NEARBY_DISCOVERY_ENABLED = booleanPreferencesKey("nearby_discovery_enabled")
        val SMS_FALLBACK_ENABLED = booleanPreferencesKey("sms_fallback_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val TRANSPORT_PRIORITY = stringPreferencesKey("transport_priority")
    }

    val settings: Flow<SinkSettings> = context.dataStore.data.map { prefs ->
        SinkSettings(
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            nearbyDiscoveryEnabled = prefs[Keys.NEARBY_DISCOVERY_ENABLED] ?: true,
            smsFallbackEnabled = prefs[Keys.SMS_FALLBACK_ENABLED] ?: true,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            transportPriorityCsv = prefs[Keys.TRANSPORT_PRIORITY] ?: SinkSettings.DEFAULT_TRANSPORT_PRIORITY,
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setNearbyDiscoveryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NEARBY_DISCOVERY_ENABLED] = enabled }
    }

    suspend fun setSmsFallbackEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SMS_FALLBACK_ENABLED] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setTransportPriority(kindsCsv: String) {
        context.dataStore.edit { it[Keys.TRANSPORT_PRIORITY] = kindsCsv }
    }
}
