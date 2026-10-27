package cloud.codella.vikunja.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import cloud.codella.vikunja.MainActivity
import cloud.codella.vikunja.R

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_REMINDERS = "vikunja_reminders"
        const val CHANNEL_DUE_DATES = "vikunja_due_dates"
        const val CHANNEL_UPDATES = "vikunja_updates"
        const val CHANNEL_SYNC = "vikunja_sync_status"

        const val ACTION_MARK_DONE = "cloud.codella.vikunja.ACTION_MARK_DONE"
        const val ACTION_SNOOZE_10M = "cloud.codella.vikunja.ACTION_SNOOZE_10M"
        const val ACTION_SNOOZE_1H = "cloud.codella.vikunja.ACTION_SNOOZE_1H"
        const val ACTION_SNOOZE_TOMORROW = "cloud.codella.vikunja.ACTION_SNOOZE_TOMORROW"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        const val SYNC_SERVICE_NOTIFICATION_ID = 9001
        const val DAILY_SUMMARY_NOTIFICATION_ID = 9002
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.reminders_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Task reminder alerts"
                enableVibration(true)
            }

            val dueDatesChannel = NotificationChannel(
                CHANNEL_DUE_DATES,
                context.getString(R.string.due_dates_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Task due date alerts"
                enableVibration(true)
            }

            val updatesChannel = NotificationChannel(
                CHANNEL_UPDATES,
                context.getString(R.string.mentions_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Server updates, assignments and comments"
            }

            val syncChannel = NotificationChannel(
                CHANNEL_SYNC,
                context.getString(R.string.sync_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background synchronization status"
            }

            notificationManager.createNotificationChannels(
                listOf(remindersChannel, dueDatesChannel, updatesChannel, syncChannel)
            )
        }
    }

    fun showTaskNotification(
        taskId: Long,
        title: String,
        body: String,
        isDueDate: Boolean = true
    ) {
        val notificationId = (taskId % 100000).toInt()
        val channelId = if (isDueDate) CHANNEL_DUE_DATES else CHANNEL_REMINDERS

        // Open Task intent
        val openIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("vikunja://task/$taskId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Mark Done action
        val doneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_DONE
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze 10m action
        val snooze10Intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_SNOOZE_10M
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snooze10PendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            snooze10Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze 1h action
        val snooze1hIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_SNOOZE_1H
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snooze1hPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 3,
            snooze1hIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full Screen Reminder Popup Intent (TickTick-style)
        val popupIntent = Intent(context, cloud.codella.vikunja.ui.screens.reminder.ReminderPopupActivity::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra("is_due_date", isDueDate)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val popupPendingIntent = PendingIntent.getActivity(
            context,
            notificationId * 10 + 4,
            popupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(popupPendingIntent, true)
            .addAction(android.R.drawable.checkbox_on_background, context.getString(R.string.mark_done), donePendingIntent)
            .addAction(android.R.drawable.ic_popup_reminder, context.getString(R.string.action_snooze_10m), snooze10PendingIntent)
            .addAction(android.R.drawable.ic_popup_reminder, context.getString(R.string.action_snooze_1h), snooze1hPendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun showServerNotification(id: Long, title: String, message: String) {
        val notificationId = 50000 + (id % 10000).toInt()
        val intent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("vikunja://notifications")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun showDailySummary(todayCount: Int, overdueCount: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("vikunja://today")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            DAILY_SUMMARY_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val content = if (overdueCount > 0) {
            "You have $todayCount tasks due today, and $overdueCount overdue tasks waiting."
        } else {
            "You have $todayCount tasks due today. Have a productive day!"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_DUE_DATES)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle(context.getString(R.string.daily_summary_title))
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(DAILY_SUMMARY_NOTIFICATION_ID, notification)
    }

    fun buildSyncForegroundNotification(): android.app.Notification {
        return NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Vikunja Background Sync")
            .setContentText("Keeping tasks and notifications up-to-date")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun buildTasksPersistentNotification(
        tasks: List<cloud.codella.vikunja.core.database.entity.TaskEntity>
    ): android.app.Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("vikunja://today")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            SYNC_SERVICE_NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Quick Add Intent
        val quickAddIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("vikunja://quick_add")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val quickAddPendingIntent = PendingIntent.getActivity(
            context,
            SYNC_SERVICE_NOTIFICATION_ID + 10,
            quickAddIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (tasks.isEmpty()) {
            return NotificationCompat.Builder(context, CHANNEL_SYNC)
                .setSmallIcon(android.R.drawable.checkbox_on_background)
                .setContentTitle(context.getString(R.string.persistent_tasks_title))
                .setContentText(context.getString(R.string.persistent_tasks_none))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openPendingIntent)
                .addAction(android.R.drawable.ic_input_add, context.getString(R.string.quick_add), quickAddPendingIntent)
                .build()
        }

        // Sort tasks: Priority DESC (5 to 1), then Due Date ASC
        val sortedTasks = tasks.sortedWith(
            compareByDescending<cloud.codella.vikunja.core.database.entity.TaskEntity> { it.priority }
                .thenBy { it.dueDate ?: "" }
        )

        val topTask = sortedTasks.first()
        val overdueCount = sortedTasks.count {
            val due = cloud.codella.vikunja.core.util.DateUtils.parseIsoToMillis(it.dueDate)
            due != null && cloud.codella.vikunja.core.util.DateUtils.isOverdue(due)
        }
        val todayCount = sortedTasks.size - overdueCount

        val title = if (overdueCount > 0) {
            "${context.getString(R.string.persistent_tasks_title)} (${context.getString(R.string.persistent_overdue_and_today, todayCount, overdueCount)})"
        } else {
            "${context.getString(R.string.persistent_tasks_title)} (${sortedTasks.size})"
        }

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)

        sortedTasks.take(5).forEach { task ->
            val priorityPrefix = when (task.priority) {
                5 -> "🔴 "
                4 -> "🟠 "
                3 -> "🟡 "
                2 -> "🔵 "
                else -> "⚪ "
            }
            val dueTime = cloud.codella.vikunja.core.util.DateUtils.parseIsoToMillis(task.dueDate)?.let { millis ->
                java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(millis))
            } ?: ""
            val timeSuffix = if (dueTime.isNotEmpty()) " • $dueTime" else ""
            inboxStyle.addLine("$priorityPrefix${task.title}$timeSuffix")
        }

        if (sortedTasks.size > 5) {
            inboxStyle.setSummaryText("+${sortedTasks.size - 5} more tasks")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle(title)
            .setContentText("Top: ${topTask.title}")
            .setStyle(inboxStyle)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_input_add, context.getString(R.string.quick_add), quickAddPendingIntent)

        // Action to mark the highest priority task done right from notification
        val doneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_DONE
            putExtra(EXTRA_TASK_ID, topTask.id)
            putExtra(EXTRA_NOTIFICATION_ID, SYNC_SERVICE_NOTIFICATION_ID)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            SYNC_SERVICE_NOTIFICATION_ID + 20,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.checkbox_on_background, "Done: ${topTask.title.take(15)}", donePendingIntent)

        return builder.build()
    }

    fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }
}
