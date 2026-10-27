package cloud.codella.vikunja.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import cloud.codella.vikunja.core.alarm.AlarmSchedulerImpl
import cloud.codella.vikunja.core.database.VikunjaDatabase
import cloud.codella.vikunja.data.work.SyncWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Reschedules all active exact alarms after device reboot, app update, or timezone change.
 * Exact alarms are cleared by the Android OS on reboot, so re-hydration is mandatory.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received broadcast intent: $action")

        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED
        )

        if (action !in validActions) {
            Log.d(TAG, "Ignoring irrelevant action: $action")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = VikunjaDatabase.getInstance(context)
                val activeTasks = db.taskDao().getTasksWithDueDate()
                val alarmScheduler = AlarmSchedulerImpl(context)
                val now = System.currentTimeMillis()
                var scheduledCount = 0

                for (task in activeTasks) {
                    if (task.done) continue
                    val dueDateIso = task.dueDate ?: continue

                    try {
                        val triggerAtMillis = Instant.parse(dueDateIso).toEpochMilli()
                        if (triggerAtMillis > now) {
                            val scheduled = alarmScheduler.scheduleExactAlarm(
                                taskId = task.id,
                                title = task.title,
                                triggerAtMillis = triggerAtMillis,
                                isDueDate = true
                            )
                            if (scheduled) scheduledCount++
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse due date '$dueDateIso' for task #${task.id}: ${e.message}")
                    }
                }

                Log.d(TAG, "Re-hydrated $scheduledCount exact alarms after event: $action")

                // Ensure periodic sync is active
                SyncWorkScheduler.enqueuePeriodicSync(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error re-hydrating alarms during $action: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
