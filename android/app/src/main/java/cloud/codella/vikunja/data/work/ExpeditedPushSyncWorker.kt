package cloud.codella.vikunja.data.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import cloud.codella.vikunja.VikunjaApp
import cloud.codella.vikunja.core.notification.NotificationHelper

/**
 * High-priority, expedited one-time worker that immediately pushes queued mutations
 * (e.g., toggled task, newly created task) to the self-hosted Vikunja server.
 */
class ExpeditedPushSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ExpeditedPushSyncWorker"
        const val SYNC_NOTIFICATION_ID = 9001
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, NotificationHelper.CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Vikunja Sync")
            .setContentText("Uploading pending changes...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                SYNC_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(SYNC_NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting expedited push sync (attempt=$runAttemptCount)...")
        val app = applicationContext as? VikunjaApp
        if (app == null) {
            Log.e(TAG, "VikunjaApp unavailable, failing sync worker.")
            return Result.failure()
        }

        return try {
            when (val syncResult = app.syncEngine.sync()) {
                is cloud.codella.vikunja.domain.model.SyncResult.Success -> {
                    Log.d(TAG, "Expedited push sync succeeded: pushed=${syncResult.pushedCount}")
                    Result.success()
                }
                is cloud.codella.vikunja.domain.model.SyncResult.Partial -> {
                    Log.w(TAG, "Expedited push sync partial success with errors: ${syncResult.errors}")
                    Result.retry()
                }
                is cloud.codella.vikunja.domain.model.SyncResult.Failure -> {
                    Log.e(TAG, "Expedited push sync error: ${syncResult.cause.message}", syncResult.cause)
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Expedited push sync error: ${e.message}", e)
            Result.retry()
        }
    }
}
