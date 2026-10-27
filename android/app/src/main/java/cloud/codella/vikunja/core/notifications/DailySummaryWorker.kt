package cloud.codella.vikunja.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cloud.codella.vikunja.VikunjaApp
import cloud.codella.vikunja.core.util.DateUtils

class DailySummaryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? VikunjaApp ?: return Result.failure()
        return try {
            val tasks = app.database.taskDao().getTasksWithDueDate()
            val now = System.currentTimeMillis()
            var todayCount = 0
            var overdueCount = 0

            for (task in tasks) {
                val dueMillis = DateUtils.parseIsoToMillis(task.dueDate) ?: continue
                if (DateUtils.isToday(dueMillis)) {
                    todayCount++
                } else if (dueMillis < now) {
                    overdueCount++
                }
            }

            if (todayCount > 0 || overdueCount > 0) {
                val helper = NotificationHelper(applicationContext)
                helper.showDailySummary(todayCount, overdueCount)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
