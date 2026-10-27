package cloud.codella.vikunja.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cloud.codella.vikunja.MainActivity
import cloud.codella.vikunja.R
import cloud.codella.vikunja.receiver.TaskNotificationActionReceiver

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val TAG = "NotificationHelper"

        const val CHANNEL_REMINDERS = "vikunja_reminders"
        const val CHANNEL_DUE_DATES = "vikunja_due_dates"
        const val CHANNEL_SYNC = "vikunja_sync_status"

        const val ACTION_MARK_DONE = "cloud.codella.vikunja.ACTION_MARK_DONE"
        const val ACTION_SNOOZE_10M = "cloud.codella.vikunja.ACTION_SNOOZE_10M"
        const val ACTION_SNOOZE_1H = "cloud.codella.vikunja.ACTION_SNOOZE_1H"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    init {
        createNotificationChannels()
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority notifications for task reminders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
            }

            val dueDatesChannel = NotificationChannel(
                CHANNEL_DUE_DATES,
                "Task Due Dates",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alerts when tasks become due"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
            }

            val syncChannel = NotificationChannel(
                CHANNEL_SYNC,
                "Sync Operations",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background synchronization notifications"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(
                listOf(remindersChannel, dueDatesChannel, syncChannel)
            )
        }
    }

    fun showTaskNotification(
        taskId: Long,
        title: String,
        body: String,
        isDueDate: Boolean = true
    ) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Skipping notification for task #$taskId.")
            return
        }

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

        // Action 1: Mark Done
        val doneIntent = Intent(context, TaskNotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_DONE
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_TASK_TITLE, title)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Snooze 10m
        val snooze10Intent = Intent(context, TaskNotificationActionReceiver::class.java).apply {
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

        // Action 3: Snooze 1h
        val snooze1hIntent = Intent(context, TaskNotificationActionReceiver::class.java).apply {
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

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.checkbox_on_background,
                "Mark Done",
                donePendingIntent
            )
            .addAction(
                android.R.drawable.ic_popup_reminder,
                "Snooze 10m",
                snooze10PendingIntent
            )
            .addAction(
                android.R.drawable.ic_popup_reminder,
                "Snooze 1h",
                snooze1hPendingIntent
            )
            .build()

        try {
            notificationManager.notify(notificationId, notification)
            Log.d(TAG, "Posted notification #$notificationId for task #$taskId")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while posting notification for task #$taskId: ${e.message}", e)
        }
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
        Log.d(TAG, "Cancelled notification #$notificationId")
    }
}
