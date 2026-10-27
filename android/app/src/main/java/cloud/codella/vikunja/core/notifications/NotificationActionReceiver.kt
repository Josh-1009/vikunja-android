package cloud.codella.vikunja.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cloud.codella.vikunja.VikunjaApp
import cloud.codella.vikunja.core.model.TaskCreateUpdateRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, 0L)
        val title = intent.getStringExtra(NotificationHelper.EXTRA_TASK_TITLE) ?: "Task"
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 0)
        val action = intent.action ?: return

        val helper = NotificationHelper(context)
        val scheduler = AlarmScheduler(context)
        helper.cancelNotification(notificationId)

        if (taskId == 0L) return

        when (action) {
            NotificationHelper.ACTION_MARK_DONE -> {
                val app = context.applicationContext as? VikunjaApp
                if (app != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            app.database.taskDao().updateTaskDone(taskId, true)
                            app.apiService.updateTask(taskId, TaskCreateUpdateRequest(title = title, done = true))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
            NotificationHelper.ACTION_SNOOZE_10M -> {
                val triggerAt = System.currentTimeMillis() + (10 * 60 * 1000)
                scheduler.scheduleExactAlarm(taskId, title, triggerAt, isDueDate = false)
            }
            NotificationHelper.ACTION_SNOOZE_1H -> {
                val triggerAt = System.currentTimeMillis() + (60 * 60 * 1000)
                scheduler.scheduleExactAlarm(taskId, title, triggerAt, isDueDate = false)
            }
            NotificationHelper.ACTION_SNOOZE_TOMORROW -> {
                val triggerAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
                scheduler.scheduleExactAlarm(taskId, title, triggerAt, isDueDate = false)
            }
        }
    }
}
