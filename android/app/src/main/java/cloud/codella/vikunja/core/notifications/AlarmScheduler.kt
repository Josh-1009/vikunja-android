package cloud.codella.vikunja.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleExactAlarm(
        taskId: Long,
        title: String,
        triggerAtMillis: Long,
        isDueDate: Boolean = true
    ) {
        if (triggerAtMillis <= System.currentTimeMillis()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // If permission is not granted, fallback to inexact window alarm
                scheduleInexactAlarm(taskId, title, triggerAtMillis, isDueDate)
                return
            }
        }

        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
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

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (e: SecurityException) {
            scheduleInexactAlarm(taskId, title, triggerAtMillis, isDueDate)
        }
    }

    private fun scheduleInexactAlarm(
        taskId: Long,
        title: String,
        triggerAtMillis: Long,
        isDueDate: Boolean
    ) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
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
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    fun cancelAlarm(taskId: Long, isDueDate: Boolean = true) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java)
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
        }
    }
}
