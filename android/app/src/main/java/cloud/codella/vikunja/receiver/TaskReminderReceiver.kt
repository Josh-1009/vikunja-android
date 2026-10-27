package cloud.codella.vikunja.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cloud.codella.vikunja.core.database.VikunjaDatabase
import cloud.codella.vikunja.core.notifications.NotificationHelper
import cloud.codella.vikunja.core.util.DescriptionUtils
import cloud.codella.vikunja.ui.screens.reminder.ReminderPopupActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Catches exact alarms scheduled by AlarmScheduler and displays
 * high-priority actionable notifications and pop-up alerts.
 */
class TaskReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskReminderReceiver"
        const val EXTRA_IS_DUE_DATE = "is_due_date"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1L)
        val fallbackTitle = intent.getStringExtra(NotificationHelper.EXTRA_TASK_TITLE) ?: "Task Reminder"
        val isDueDate = intent.getBooleanExtra(EXTRA_IS_DUE_DATE, true)

        Log.d(TAG, "Alarm received for task #$taskId (isDueDate=$isDueDate)")

        if (taskId <= 0) {
            Log.w(TAG, "Invalid taskId received: $taskId")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = VikunjaDatabase.getInstance(context)
                val task = db.taskDao().getTaskById(taskId)

                if (task != null && task.done) {
                    Log.d(TAG, "Task #$taskId is already marked done. Suppressing notification.")
                    return@launch
                }

                val title = task?.title ?: fallbackTitle
                val cleanDesc = DescriptionUtils.cleanSnippet(task?.description)
                val body = when {
                    cleanDesc.isNotBlank() -> cleanDesc
                    isDueDate -> "This task is due now."
                    else -> "Task reminder alert."
                }

                val notificationHelper = NotificationHelper(context)
                notificationHelper.showTaskNotification(
                    taskId = taskId,
                    title = title,
                    body = body,
                    isDueDate = isDueDate
                )

                // Trigger TickTick-style full-screen Pop-up Activity
                try {
                    val popupIntent = Intent(context, ReminderPopupActivity::class.java).apply {
                        putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
                        putExtra(NotificationHelper.EXTRA_TASK_TITLE, title)
                        putExtra("is_due_date", isDueDate)
                        putExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, (taskId % 100000).toInt())
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    context.startActivity(popupIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "Direct activity launch fell back to full-screen notification intent: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display reminder notification for task #$taskId: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
