package cloud.codella.vikunja.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cloud.codella.vikunja.VikunjaApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, 0L)
        val title = intent.getStringExtra(NotificationHelper.EXTRA_TASK_TITLE) ?: "Task Reminder"
        val isDueDate = intent.getBooleanExtra("is_due_date", true)

        if (taskId != 0L) {
            val helper = NotificationHelper(context)
            val body = if (isDueDate) "This task is due now!" else "You set a reminder for this task"
            helper.showTaskNotification(taskId, title, body, isDueDate)

            // Also launch directly with NEW_TASK to guarantee popup even if fullScreenIntent is throttled by OEM
            try {
                val popupIntent = Intent(context, cloud.codella.vikunja.ui.screens.reminder.ReminderPopupActivity::class.java).apply {
                    putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
                    putExtra(NotificationHelper.EXTRA_TASK_TITLE, title)
                    putExtra("is_due_date", isDueDate)
                    putExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, (taskId % 100000).toInt())
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(popupIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Nagging Alarm Support: If task is urgent (Priority 5) and nagging mode is on, repeat in 2 minutes
            val app = context.applicationContext as? VikunjaApp
            if (app != null) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        val task = app.database.taskDao().getTaskById(taskId)
                        val naggingEnabled = app.preferencesManager.naggingAlarmEnabledFlow.first()
                        if (task != null && !task.done && task.priority >= 5 && naggingEnabled) {
                            val scheduler = AlarmScheduler(context)
                            val nextNag = System.currentTimeMillis() + (2 * 60 * 1000)
                            scheduler.scheduleExactAlarm(taskId, title, nextNag, isDueDate = false)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
