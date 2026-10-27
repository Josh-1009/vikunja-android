package cloud.codella.vikunja.data.work

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Manages WorkManager jobs for periodic downstream sync and expedited mutation pushes.
 */
object SyncWorkScheduler {

    private const val TAG = "SyncWorkScheduler"
    const val PERIODIC_SYNC_WORK_NAME = "vikunja_clean_periodic_sync"
    const val EXPEDITED_PUSH_WORK_NAME = "vikunja_clean_expedited_push"

    /**
     * Schedules periodic background sync every [intervalMinutes] (default: 15) when connected to a network.
     */
    fun enqueuePeriodicSync(context: Context, intervalMinutes: Long = 15) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false)
            .build()

        val request = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
        Log.d(TAG, "Enqueued periodic sync every $intervalMinutes minutes")
    }

    /**
     * Immediately triggers an expedited push sync worker to flush pending mutations to the server.
     * Uses [OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST] so it gracefully degrades if background quota is exhausted.
     */
    fun triggerExpeditedPush(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ExpeditedPushSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            EXPEDITED_PUSH_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
        Log.d(TAG, "Triggered expedited push sync work")
    }

    /**
     * Cancels all scheduled sync jobs.
     */
    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
        wm.cancelUniqueWork(EXPEDITED_PUSH_WORK_NAME)
        Log.d(TAG, "Cancelled all sync background work")
    }
}
