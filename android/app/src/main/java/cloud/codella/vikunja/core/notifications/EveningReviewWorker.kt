package cloud.codella.vikunja.core.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cloud.codella.vikunja.R
import cloud.codella.vikunja.VikunjaApp
import cloud.codella.vikunja.core.util.DateUtils
import kotlinx.coroutines.flow.first

class EveningReviewWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? VikunjaApp ?: return Result.failure()
        return try {
            val isEnabled = app.preferencesManager.eveningReviewEnabledFlow.first()
            if (!isEnabled) return Result.success()

            val allTasks = app.database.taskDao().getAllTasksFlow().first()
            var completedToday = 0
            var remainingToday = 0

            for (task in allTasks) {
                if (task.done) {
                    val doneMillis = DateUtils.parseIsoToMillis(task.doneAt)
                    if (doneMillis != null && DateUtils.isToday(doneMillis)) {
                        completedToday++
                    }
                } else {
                    val dueMillis = DateUtils.parseIsoToMillis(task.dueDate)
                    if (dueMillis != null && (DateUtils.isToday(dueMillis) || DateUtils.isOverdue(dueMillis))) {
                        remainingToday++
                    }
                }
            }

            if (completedToday > 0 || remainingToday > 0) {
                val helper = NotificationHelper(applicationContext)
                val title = applicationContext.getString(R.string.evening_review_title)
                val body = if (remainingToday == 0) {
                    "🎉 Amazing job! You completed all $completedToday tasks today!"
                } else {
                    "You crushed $completedToday tasks today! $remainingToday tasks remaining."
                }
                helper.showServerNotification(9903L, title, body)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
