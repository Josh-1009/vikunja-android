package cloud.codella.vikunja.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cloud.codella.vikunja.VikunjaApp
import cloud.codella.vikunja.core.alarm.AlarmSchedulerImpl
import cloud.codella.vikunja.core.database.VikunjaDatabase
import cloud.codella.vikunja.core.notification.NotificationHelper
import cloud.codella.vikunja.data.work.SyncWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles actionable notification buttons clicked by the user:
 * - "Mark Done": Marks task completed in Room, enqueues sync mutation, and triggers expedited push sync.
 * - "Snooze 10m" / "Snooze 1h": Dismisses current alert and reschedules exact alarm into the future.
 */
class TaskNotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskNotifActionReceiver"
        private const val SNOOZE_10_MIN_MILLIS = 10 * 60 * 1000L
        private const val SNOOZE_1_HOUR_MILLIS = 60 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1L)
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        val title = intent.getStringExtra(NotificationHelper.EXTRA_TASK_TITLE) ?: "Task Reminder"

        Log.d(TAG, "Received action '$action' for task #$taskId (notificationId=$notificationId)")

        if (notificationId != -1) {
            val notificationHelper = NotificationHelper(context)
            notificationHelper.cancelNotification(notificationId)
        }

        if (taskId <= 0) {
            Log.w(TAG, "Invalid taskId: $taskId, ignoring action.")
            return
        }

        val alarmScheduler = AlarmSchedulerImpl(context)

        when (action) {
            NotificationHelper.ACTION_MARK_DONE -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = context.applicationContext as? VikunjaApp
                        if (app != null) {
                            app.cleanTaskRepository.toggleTaskDone(taskId, isDone = true)
                            Log.d(TAG, "Task #$taskId marked as done via cleanTaskRepository")
                        } else {
                            val db = VikunjaDatabase.getInstance(context)
                            db.taskDao().updateTaskDone(taskId, true)
                            Log.w(TAG, "VikunjaApp unavailable, updated task #$taskId directly in Room")
                        }

                        // Cancel pending alarm for due date if any
                        alarmScheduler.cancelTaskReminder(taskId, isDueDate = true)

                        // Trigger immediate expedited push sync
                        SyncWorkScheduler.triggerExpeditedPush(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to mark task #$taskId done from notification: ${e.message}", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            NotificationHelper.ACTION_SNOOZE_10M -> {
                val triggerAtMillis = System.currentTimeMillis() + SNOOZE_10_MIN_MILLIS
                alarmScheduler.scheduleExactAlarm(
                    taskId = taskId,
                    title = title,
                    triggerAtMillis = triggerAtMillis,
                    isDueDate = false
                )
                Log.d(TAG, "Snoozed task #$taskId for 10 minutes (trigger=$triggerAtMillis)")
            }

            NotificationHelper.ACTION_SNOOZE_1H -> {
                val triggerAtMillis = System.currentTimeMillis() + SNOOZE_1_HOUR_MILLIS
                alarmScheduler.scheduleExactAlarm(
                    taskId = taskId,
                    title = title,
                    triggerAtMillis = triggerAtMillis,
                    isDueDate = false
                )
                Log.d(TAG, "Snoozed task #$taskId for 1 hour (trigger=$triggerAtMillis)")
            }

            else -> {
                Log.w(TAG, "Unknown action: $action")
            }
        }
    }
}
