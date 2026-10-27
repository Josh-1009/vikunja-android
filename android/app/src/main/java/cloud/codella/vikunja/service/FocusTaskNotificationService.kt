package cloud.codella.vikunja.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import cloud.codella.vikunja.MainActivity
import cloud.codella.vikunja.VikunjaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ongoing Sticky Focus Notification Service providing status-bar tracking, native chronometer,
 * and direct actionable completion ("Mark Done" and "Stop Focus") from the notification shade.
 */
class FocusTaskNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "vikunja_sticky_focus_channel"
        const val NOTIFICATION_ID = 9602

        const val ACTION_START_FOCUS = "cloud.codella.vikunja.action.START_FOCUS"
        const val ACTION_COMPLETE_FOCUS_TASK = "cloud.codella.vikunja.action.COMPLETE_FOCUS_TASK"
        const val ACTION_DISMISS_FOCUS = "cloud.codella.vikunja.action.DISMISS_FOCUS"
        const val ACTION_TOGGLE_PAUSE = "cloud.codella.vikunja.action.TOGGLE_PAUSE"

        const val EXTRA_TASK_ID = "extra_focus_task_id"
        const val EXTRA_TASK_TITLE = "extra_focus_task_title"
        const val EXTRA_PROJECT_TITLE = "extra_focus_project_title"

        private val _activeFocusTaskId = MutableStateFlow<Long?>(null)
        val activeFocusTaskId: StateFlow<Long?> = _activeFocusTaskId.asStateFlow()

        fun start(context: Context, taskId: Long, taskTitle: String, projectTitle: String = "Inbox") {
            val intent = Intent(context, FocusTaskNotificationService::class.java).apply {
                action = ACTION_START_FOCUS
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, taskTitle)
                putExtra(EXTRA_PROJECT_TITLE, projectTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusTaskNotificationService::class.java).apply {
                action = ACTION_DISMISS_FOCUS
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentTaskId: Long = 0L
    private var currentTaskTitle: String = ""
    private var currentProjectTitle: String = ""
    private var startTimeMillis: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observePomodoroState()
    }

    private fun observePomodoroState() {
        serviceScope.launch {
            val app = applicationContext as? VikunjaApp ?: return@launch
            app.pomodoroEngine.state.collect { pomodoroState ->
                if (_activeFocusTaskId.value != null && pomodoroState.isRunning) {
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, buildOngoingNotification())
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOCUS -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
                val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Task Focus"
                val projectTitle = intent.getStringExtra(EXTRA_PROJECT_TITLE) ?: "Vikunja"
                startFocusSession(taskId, taskTitle, projectTitle)
            }

            ACTION_COMPLETE_FOCUS_TASK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, currentTaskId)
                completeCurrentTask(taskId)
            }

            ACTION_TOGGLE_PAUSE -> {
                val app = applicationContext as? VikunjaApp
                val engine = app?.pomodoroEngine
                if (engine != null) {
                    if (engine.state.value.isPaused) {
                        engine.resume()
                    } else {
                        engine.pause()
                    }
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, buildOngoingNotification())
                }
            }

            ACTION_DISMISS_FOCUS -> {
                val app = applicationContext as? VikunjaApp
                app?.pomodoroEngine?.stop()
                stopFocusSession()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Session (Sticky)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live running focus chronometer and quick actions in notification shade"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startFocusSession(taskId: Long, taskTitle: String, projectTitle: String) {
        currentTaskId = taskId
        currentTaskTitle = taskTitle
        currentProjectTitle = projectTitle
        startTimeMillis = System.currentTimeMillis()
        _activeFocusTaskId.value = taskId

        val notification = buildOngoingNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun completeCurrentTask(taskId: Long) {
        serviceScope.launch {
            val app = applicationContext as? VikunjaApp
            if (app != null && taskId != 0L) {
                runCatching {
                    app.cleanTaskRepository.toggleTaskDone(taskId, true)
                    cloud.codella.vikunja.data.work.SyncWorkScheduler.triggerExpeditedPush(applicationContext)
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, "✓ Focus task completed!", Toast.LENGTH_SHORT).show()
            }
            stopFocusSession()
        }
    }

    private fun stopFocusSession() {
        _activeFocusTaskId.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildOngoingNotification(): Notification {
        // Tap notification to open task details in Vikunja
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("extra_task_id", currentTaskId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            101,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: Mark Done
        val markDoneIntent = Intent(this, FocusTaskNotificationService::class.java).apply {
            action = ACTION_COMPLETE_FOCUS_TASK
            putExtra(EXTRA_TASK_ID, currentTaskId)
        }
        val markDonePendingIntent = PendingIntent.getService(
            this,
            102,
            markDoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: Toggle Pause / Resume
        val togglePauseIntent = Intent(this, FocusTaskNotificationService::class.java).apply {
            action = ACTION_TOGGLE_PAUSE
        }
        val togglePausePendingIntent = PendingIntent.getService(
            this,
            104,
            togglePauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 3: Stop Focus
        val stopFocusIntent = Intent(this, FocusTaskNotificationService::class.java).apply {
            action = ACTION_DISMISS_FOCUS
        }
        val stopFocusPendingIntent = PendingIntent.getService(
            this,
            103,
            stopFocusIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val app = applicationContext as? VikunjaApp
        val pomodoro = app?.pomodoroEngine?.state?.value
        val isPomodoroActive = pomodoro != null && pomodoro.mode != cloud.codella.vikunja.core.timer.PomodoroMode.IDLE

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)

        if (isPomodoroActive && pomodoro != null) {
            val minutes = pomodoro.remainingSeconds / 60
            val seconds = pomodoro.remainingSeconds % 60
            val formattedTime = String.format("%02d:%02d", minutes, seconds)
            val pauseLabel = if (pomodoro.isPaused) "Resume" else "Pause"

            builder.setContentTitle("🍅 ${pomodoro.mode.displayName}: $currentTaskTitle")
                .setContentText("$formattedTime • Cycle ${pomodoro.currentCycle}/4 • $currentProjectTitle")
                .setUsesChronometer(false)
                .setShowWhen(false)
                .addAction(
                    if (pomodoro.isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                    pauseLabel,
                    togglePausePendingIntent
                )
        } else {
            builder.setContentTitle("🎯 Focus: $currentTaskTitle")
                .setContentText("Project: $currentProjectTitle • Focusing")
                .setUsesChronometer(true)
                .setWhen(startTimeMillis)
                .setShowWhen(true)
        }

        builder.addAction(
            android.R.drawable.checkbox_on_background,
            "Mark Done",
            markDonePendingIntent
        ).addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop Focus",
            stopFocusPendingIntent
        )

        return builder.build()
    }

    override fun onDestroy() {
        _activeFocusTaskId.value = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
