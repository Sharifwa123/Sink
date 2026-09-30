package com.sharif.sink.networking.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sharif.sink.database.dao.ContactDao
import com.sharif.sink.datastore.SinkPreferences
import com.sharif.sink.mesh.NetworkStatus
import com.sharif.sink.mesh.RoutingEngine
import com.sharif.sink.mesh.TransportManager
import com.sharif.sink.protocol.Message
import com.sharif.sink.protocol.TransportKind
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val STATUS_CHANNEL_ID = "sink_mesh_status"
private const val STATUS_NOTIFICATION_ID = 1001
private const val MESSAGE_CHANNEL_ID = "sink_incoming_messages"
const val ACTION_START_MESH = "com.sharif.sink.action.START_MESH"
const val ACTION_STOP_MESH = "com.sharif.sink.action.STOP_MESH"

/** Read by MainActivity to jump straight into a chat when a message notification is tapped. */
const val EXTRA_PEER_DEVICE_ID = "com.sharif.sink.extra.PEER_DEVICE_ID"

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
    lateinit var routingEngine: RoutingEngine

    @Inject
    lateinit var peerIdentityPersister: PeerIdentityPersister

    @Inject
    lateinit var contactDao: ContactDao

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
        startForeground(STATUS_NOTIFICATION_ID, buildStatusNotification(NetworkStatus.OFFLINE))

        peerIdentityPersister.start()

        // Nearby discovery can be turned off in Settings — honor that by not activating the
        // local-mesh radio, but every transport's listeners are always wired (via
        // TransportManager.start's `activate` set) so turning nearby discovery off never also
        // breaks unrelated transports like SMS. MeshConnectivityMonitor retries the local-mesh
        // transport automatically the moment the setting is flipped back on, so this doesn't
        // require reopening the app.
        serviceScope.launch {
            val nearbyEnabled = preferences.settings.first().nearbyDiscoveryEnabled
            val activate = if (nearbyEnabled) {
                TransportKind.entries.toSet()
            } else {
                TransportKind.entries.toSet() - TransportKind.LOCAL_MESH
            }
            transportManager.start(activate)
        }

        transportManager.networkStatus
            .onEach { status ->
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(STATUS_NOTIFICATION_ID, buildStatusNotification(status))
            }
            .launchIn(serviceScope)

        routingEngine.incomingMessages
            .onEach { message -> handleIncomingMessage(message) }
            .launchIn(serviceScope)
    }

    private suspend fun handleIncomingMessage(message: Message) {
        if (!preferences.settings.first().notificationsEnabled) return
        val senderId = message.senderId.value
        val senderName = contactDao.get(senderId)?.displayName?.takeIf { it.isNotBlank() } ?: senderId
        notifyIncomingMessage(senderId, senderName)
    }

    private fun notifyIncomingMessage(senderId: String, senderName: String) {
        // Tapping the notification launches the app with the sender's id attached rather than
        // referencing MainActivity directly — core:networking can't depend on :app — MainActivity
        // reads this same EXTRA_PEER_DEVICE_ID key and jumps straight into that chat.
        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_PEER_DEVICE_ID, senderId)
        } ?: return

        val pendingIntent = PendingIntent.getActivity(
            this,
            senderId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setContentTitle(senderName)
            // Never the decrypted body here: a notification can surface on a locked screen, and
            // Sink keeps plaintext off of everything except the chat screen itself — see
            // docs/SECURITY.md on what SinkLogger and friends are and aren't allowed to surface.
            .setContentText("Sent you a message")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java)?.notify(senderId.hashCode(), notification)
    }

    private fun buildStatusNotification(status: NetworkStatus): Notification {
        val text = when (status) {
            NetworkStatus.ONLINE -> "Connected to the internet"
            NetworkStatus.LOCAL_MESH -> "Connected through the local mesh"
            NetworkStatus.NEARBY -> "Connected to a nearby Sink device"
            NetworkStatus.SMS_FALLBACK -> "Using SMS fallback"
            NetworkStatus.OFFLINE -> "Looking for nearby Sink devices…"
        }
        return NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("Sink")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(STATUS_CHANNEL_ID, "Mesh network status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows whether Sink is connected to nearby devices or the internet"
            },
        )
        manager?.createNotificationChannel(
            NotificationChannel(MESSAGE_CHANNEL_ID, "New messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notifies you when a new message arrives"
            },
        )
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
