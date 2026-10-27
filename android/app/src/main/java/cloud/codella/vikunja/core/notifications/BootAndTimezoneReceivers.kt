package cloud.codella.vikunja.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cloud.codella.vikunja.VikunjaApp
import cloud.codella.vikunja.core.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            rescheduleAllAlarms(context)
        }
    }
}

class TimezoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED || intent.action == Intent.ACTION_TIME_CHANGED) {
            rescheduleAllAlarms(context)
        }
    }
}

private fun rescheduleAllAlarms(context: Context) {
    val app = context.applicationContext as? VikunjaApp ?: return
    val scheduler = AlarmScheduler(context)

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val tasks = app.database.taskDao().getTasksWithDueDate()
            val now = System.currentTimeMillis()
            for (task in tasks) {
                val dueDateMillis = DateUtils.parseIsoToMillis(task.dueDate)
                if (dueDateMillis != null && dueDateMillis > now) {
                    scheduler.scheduleExactAlarm(
                        taskId = task.id,
                        title = task.title,
                        triggerAtMillis = dueDateMillis,
                        isDueDate = true
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
