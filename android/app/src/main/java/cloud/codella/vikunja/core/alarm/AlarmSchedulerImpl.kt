package cloud.codella.vikunja.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import cloud.codella.vikunja.core.notification.NotificationHelper
import cloud.codella.vikunja.domain.model.Task
import cloud.codella.vikunja.receiver.TaskReminderReceiver
import java.time.Instant

class AlarmSchedulerImpl(
    private val context: Context
) : AlarmScheduler {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "AlarmScheduler"
    }

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    override fun scheduleTaskReminder(task: Task): Boolean {
        if (task.done) {
            Log.d(TAG, "Task ${task.id} is already completed. Skipping reminder scheduling.")
            return false
        }

        val targetDateIso = task.dueDate ?: return false
        val triggerAtMillis = try {
            Instant.parse(targetDateIso).toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse ISO date '$targetDateIso' for task ${task.id}: ${e.message}")
            return false
        }

        return scheduleExactAlarm(
            taskId = task.id,
            title = task.title,
            triggerAtMillis = triggerAtMillis,
            isDueDate = true
        )
    }

    override fun scheduleExactAlarm(
        taskId: Long,
        title: String,
        triggerAtMillis: Long,
        isDueDate: Boolean
    ): Boolean {
        val now = System.currentTimeMillis()
        if (triggerAtMillis <= now) {
            Log.w(TAG, "Cannot schedule alarm for task #$taskId: trigger timestamp ($triggerAtMillis) is in the past (now=$now).")
            return false
        }

        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            putExtra(NotificationHelper.EXTRA_TASK_ID, taskId)
            putExtra(NotificationHelper.EXTRA_TASK_TITLE, title)
            putExtra("is_due_date", isDueDate)
        }

        val requestCode = (taskId * 2 + (if (isDueDate) 0 else 1)).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Android 12+ (API 31+) & Android 14+ (API 34+) exact alarm checks
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
            Log.w(TAG, "SCHEDULE_EXACT_ALARM permission not granted for task #$taskId. Falling back to setAndAllowWhileIdle.")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            return false
        }

        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            Log.d(TAG, "Exact alarm scheduled successfully for task #$taskId at $triggerAtMillis")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while scheduling exact alarm for task #$taskId: ${e.message}. Falling back to inexact alarm.", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            false
        }
    }

    override fun cancelTaskReminder(taskId: Long, isDueDate: Boolean) {
        val intent = Intent(context, TaskReminderReceiver::class.java)
        val requestCode = (taskId * 2 + (if (isDueDate) 0 else 1)).toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled alarm for task #$taskId (requestCode=$requestCode)")
        }
    }
}
