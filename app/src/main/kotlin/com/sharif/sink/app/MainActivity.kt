package com.sharif.sink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sharif.sink.app.navigation.SinkNavHost
import com.sharif.sink.app.ui.theme.SinkTheme
import com.sharif.sink.networking.mesh.MeshForegroundService
import com.sharif.sink.permissions.PermissionChecker
import com.sharif.sink.permissions.SinkPermission
import com.sharif.sink.permissions.manifestPermissions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var permissionChecker: PermissionChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var nearbyPermissionGranted by remember {
                mutableStateOf(permissionChecker.isGranted(SinkPermission.NEARBY_DEVICES))
            }

            val nearbyPermissionLauncher = rememberPermissionLauncher { granted ->
                nearbyPermissionGranted = granted
            }

            // A mesh session is only kept alive while Sink is actually visible to the user —
            // never as an unconditional background daemon. See docs/ANDROID_LIMITATIONS.md.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> MeshForegroundService.start(this@MainActivity)
                        Lifecycle.Event.ON_STOP -> MeshForegroundService.stop(this@MainActivity)
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            SinkTheme {
                SinkNavHost(
                    nearbyPermissionGranted = nearbyPermissionGranted,
                    onRequestNearbyPermission = {
                        nearbyPermissionLauncher.launch(SinkPermission.NEARBY_DEVICES.manifestPermissions().toTypedArray())
                    },
                )
            }
        }
    }

    @Composable
    private fun rememberPermissionLauncher(onResult: (Boolean) -> Unit) =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { results -> onResult(results.values.all { it }) }
}
