package com.sharif.sink.networking.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sharif.sink.mesh.RoutingEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

private const val UNIQUE_WORK_NAME = "sink_mesh_retry_sweep"

/**
 * Safety-net periodic retry: most retries happen immediately when a peer
 * reconnects (see RoutingEngine's peer-connected listener), but if the app
 * process was killed and later restarted without a live peer-connect
 * event, this is what still eventually retries a queued message. 15
 * minutes is WorkManager's minimum periodic interval — Sink doesn't fight
 * that with a foreground service just to retry faster; that would be
 * exactly the kind of unnecessary background battery drain this design
 * avoids.
 */
@HiltWorker
class MeshRetryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val routingEngine: RoutingEngine,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        routingEngine.retrySweep()
        return Result.success()
    }
}

object MeshRetryScheduler {
    fun schedulePeriodicSweep(context: Context) {
        val request = PeriodicWorkRequestBuilder<MeshRetryWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
