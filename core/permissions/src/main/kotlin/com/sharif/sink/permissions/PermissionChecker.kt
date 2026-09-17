package com.sharif.sink.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** True if every manifest permission this bundle needs on this OS version is already granted. */
    fun isGranted(permission: SinkPermission): Boolean =
        permission.manifestPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Per-radio breakdown of [SinkPermission.NEARBY_DEVICES], for surfacing in
     * diagnostics: the runtime request bundles these into one dialog flow, but
     * users should still be able to see that Bluetooth and Wi-Fi specifically
     * — not some vague "nearby devices" concept — are what the mesh uses.
     */
    fun meshRadioStatus(): List<Pair<String, Boolean>> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add("Bluetooth" to listOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE,
                android.Manifest.permission.BLUETOOTH_CONNECT,
            ).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
        } else {
            add("Bluetooth (via location)" to
                (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add("Wi-Fi (nearby devices)" to
                (ContextCompat.checkSelfPermission(context, android.Manifest.permission.NEARBY_WIFI_DEVICES) ==
                    PackageManager.PERMISSION_GRANTED))
        } else {
            add("Wi-Fi" to true) // No separate runtime permission pre-Android 13; ACCESS_WIFI_STATE is normal/install-time.
        }
    }
}
