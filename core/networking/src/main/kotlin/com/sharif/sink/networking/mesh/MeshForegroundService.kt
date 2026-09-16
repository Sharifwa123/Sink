package com.sharif.sink.networking.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sharif.sink.datastore.SinkPreferences
import com.sharif.sink.mesh.NetworkStatus
import com.sharif.sink.mesh.TransportManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val CHANNEL_ID = "sink_mesh_status"
private const val NOTIFICATION_ID = 1001
const val ACTION_START_MESH = "com.sharif.sink.action.START_MESH"
const val ACTION_STOP_MESH = "com.sharif.sink.action.STOP_MESH"

/**
 * Foreground service that keeps local mesh discovery/connections alive
 * while the user is actively viewing a mesh-related screen (discovery,
 * chat, home) or has a message in flight. Never started unconditionally
 * at boot or kept alive indefinitely in the background — see
 * docs/ANDROID_LIMITATIONS.md §"background behavior" for why continuous
 * background scanning isn't something this app can promise on stock
 * Android.
 */
@AndroidEntryPoint
class MeshForegroundService : Service() {

    @Inject
    lateinit var transportManager: TransportManager

    @Inject
    lateinit var peerIdentityPersister: PeerIdentityPersister

    @Inject
    lateinit var preferences: SinkPreferences

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MESH -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startAsForeground()
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        startForeground(NOTIFICATION_ID, buildNotification(NetworkStatus.OFFLINE))

        peerIdentityPersister.start()

        // Nearby discovery can be turned off in Settings — honor that rather than starting
        // radios/advertising regardless. Read once at session start: TransportManager doesn't
        // support a safe partial stop/restart mid-session, so toggling this setting takes
        // effect the next time Sink is opened, not instantly — a disclosed limitation, not a
        // silently-ignored control.
        serviceScope.launch {
            if (preferences.settings.first().nearbyDiscoveryEnabled) {
                transportManager.start()
            }
        }

        transportManager.networkStatus
            .onEach { status ->
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, buildNotification(status))
            }
            .launchIn(serviceScope)
    }

    private fun buildNotification(status: NetworkStatus): Notification {
        val text = when (status) {
            NetworkStatus.ONLINE -> "Connected to the internet"
            NetworkStatus.LOCAL_MESH -> "Connected through the local mesh"
            NetworkStatus.NEARBY -> "Connected to a nearby Sink device"
            NetworkStatus.SMS_FALLBACK -> "Using SMS fallback"
            NetworkStatus.OFFLINE -> "Looking for nearby Sink devices…"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sink")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mesh network status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows whether Sink is connected to nearby devices or the internet"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        serviceScope.launch { transportManager.stop() }
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).setAction(ACTION_START_MESH)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MeshForegroundService::class.java).setAction(ACTION_STOP_MESH))
        }
    }
}
