package cloud.codella.vikunja.core.notifications

import android.app.Service
import android.content.Intent
import android.os.IBinder
import cloud.codella.vikunja.VikunjaApp
import kotlinx.coroutines.*

class PersistentNotificationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val helper = NotificationHelper(this)
        startForeground(
            NotificationHelper.SYNC_SERVICE_NOTIFICATION_ID,
            helper.buildSyncForegroundNotification()
        )

        observeAndSyncTasks()
        return START_STICKY
    }

    private fun observeAndSyncTasks() {
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            val app = applicationContext as? VikunjaApp
            val helper = NotificationHelper(this@PersistentNotificationService)

            // Collect active tasks and update status bar notification
            val notificationJob = launch {
                app?.database?.taskDao()?.getAllTasksFlow()?.collect { allTasks ->
                    val relevantTasks = allTasks.filter { task ->
                        !task.done && task.dueDate?.let { dueIso ->
                            val millis = cloud.codella.vikunja.core.util.DateUtils.parseIsoToMillis(dueIso)
                            millis != null && (cloud.codella.vikunja.core.util.DateUtils.isToday(millis) || cloud.codella.vikunja.core.util.DateUtils.isOverdue(millis))
                        } == true
                    }
                    val notification = helper.buildTasksPersistentNotification(relevantTasks)
                    val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                    manager.notify(NotificationHelper.SYNC_SERVICE_NOTIFICATION_ID, notification)
                }
            }

            // Periodic sync loop
            while (isActive) {
                try {
                    app?.syncRepository?.syncAll()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(60_000) // 1 minute interval for persistent mode
            }
            notificationJob.cancel()
        }
    }

    override fun onDestroy() {
        syncJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
