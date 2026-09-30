package com.sharif.sink.app

import android.content.Intent
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
import com.sharif.sink.networking.mesh.EXTRA_PEER_DEVICE_ID
import com.sharif.sink.networking.mesh.MeshConnectivityMonitor
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

    @Inject
    lateinit var meshConnectivityMonitor: MeshConnectivityMonitor

    // Set by a tapped message notification (see MeshForegroundService); plain mutableStateOf
    // rather than `remember` because onNewIntent runs outside the composition and still needs
    // to be able to push a new value into it.
    private var pendingChatPeerId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingChatPeerId = intent?.getStringExtra(EXTRA_PEER_DEVICE_ID)

        setContent {
            var nearbyPermissionGranted by remember {
                mutableStateOf(permissionChecker.isGranted(SinkPermission.NEARBY_DEVICES))
            }

            val nearbyPermissionLauncher = rememberPermissionLauncher { granted ->
                nearbyPermissionGranted = granted
                // Retries the local-mesh transport immediately if this just turned true, instead
                // of leaving it stuck until the next unrelated recomposition notices.
                meshConnectivityMonitor.refresh()
            }

            val notificationsPermissionLauncher = rememberPermissionLauncher {}

            // A mesh session is only kept alive while Sink is actually visible to the user —
            // never as an unconditional background daemon. See docs/ANDROID_LIMITATIONS.md.
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> MeshForegroundService.start(this@MainActivity)
                        Lifecycle.Event.ON_RESUME -> meshConnectivityMonitor.refresh()
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
                    onRequestNotificationsPermission = {
                        val permissions = SinkPermission.NOTIFICATIONS.manifestPermissions().toTypedArray()
                        if (permissions.isNotEmpty()) notificationsPermissionLauncher.launch(permissions)
                    },
                    pendingChatPeerId = pendingChatPeerId,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingChatPeerId = intent.getStringExtra(EXTRA_PEER_DEVICE_ID)
    }

    @Composable
    private fun rememberPermissionLauncher(onResult: (Boolean) -> Unit) =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { results -> onResult(results.values.all { it }) }
}
