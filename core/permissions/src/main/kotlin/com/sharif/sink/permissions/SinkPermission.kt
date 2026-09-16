package com.sharif.sink.permissions

import android.os.Build

/**
 * A permission "bundle" as the user understands it, not a raw manifest
 * string. One feature the user cares about (nearby discovery) can require
 * different manifest permissions on different API levels — that mapping
 * lives here, once, instead of scattered across feature code.
 */
enum class SinkPermission {
    NEARBY_DEVICES,
    NOTIFICATIONS,
    SEND_SMS,
}

/** The actual manifest permission strings [SinkPermission] resolves to on the running OS version. */
fun SinkPermission.manifestPermissions(): List<String> = when (this) {
    SinkPermission.NEARBY_DEVICES -> buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(android.Manifest.permission.BLUETOOTH_SCAN)
            add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Pre-Android 12: classic Bluetooth discovery requires coarse location.
            add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    SinkPermission.NOTIFICATIONS -> buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        // Pre-Android 13: notification permission is granted at install time, nothing to request.
    }

    SinkPermission.SEND_SMS -> listOf(android.Manifest.permission.SEND_SMS)
}

/** Human-facing copy shown before a runtime permission request — never request without context. */
data class PermissionRationale(
    val title: String,
    val explanation: String,
)

fun SinkPermission.rationale(): PermissionRationale = when (this) {
    SinkPermission.NEARBY_DEVICES -> PermissionRationale(
        title = "Nearby device permission",
        explanation = "Sink uses this to discover and connect to other Sink devices near you, " +
            "so messages can reach people even without internet or mobile data.",
    )

    SinkPermission.NOTIFICATIONS -> PermissionRationale(
        title = "Notifications",
        explanation = "Sink can let you know when a message arrives or is delivered, " +
            "even while the app is in the background.",
    )

    SinkPermission.SEND_SMS -> PermissionRationale(
        title = "Send SMS",
        explanation = "Sink only uses SMS as a fallback when no nearby Sink device or " +
            "internet route is available, and only after you choose to send that way.",
    )
}
