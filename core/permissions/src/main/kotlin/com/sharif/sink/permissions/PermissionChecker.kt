package com.sharif.sink.permissions

import android.content.Context
import android.content.pm.PackageManager
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
}
