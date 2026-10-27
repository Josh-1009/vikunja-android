package cloud.codella.vikunja.data.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cloud.codella.vikunja.VikunjaApp

/**
 * Background periodic sync worker executed every 15 minutes when connected to network.
 * Synchronizes offline mutations with the self-hosted Vikunja instance and pulls latest state.
 */
class PeriodicSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "PeriodicSyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting periodic sync execution (runAttempt=$runAttemptCount)...")
        val app = applicationContext as? VikunjaApp
        if (app == null) {
            Log.e(TAG, "Application context is not VikunjaApp, aborting sync.")
            return Result.failure()
        }

        return try {
            when (val syncResult = app.syncEngine.sync()) {
                is cloud.codella.vikunja.domain.model.SyncResult.Success -> {
                    Log.d(TAG, "Periodic sync completed: pushed=${syncResult.pushedCount}, pulled=${syncResult.pulledCount}")
                    Result.success()
                }
                is cloud.codella.vikunja.domain.model.SyncResult.Partial -> {
                    Log.w(TAG, "Periodic sync completed partially with errors: ${syncResult.errors}")
                    if (runAttemptCount < 3) Result.retry() else Result.success()
                }
                is cloud.codella.vikunja.domain.model.SyncResult.Failure -> {
                    Log.e(TAG, "Periodic sync failed: ${syncResult.cause.message}", syncResult.cause)
                    if (runAttemptCount < 3) Result.retry() else Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during periodic sync: ${e.message}", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
