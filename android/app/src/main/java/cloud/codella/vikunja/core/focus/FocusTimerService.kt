package cloud.codella.vikunja.core.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import cloud.codella.vikunja.MainActivity
import cloud.codella.vikunja.R
import cloud.codella.vikunja.VikunjaApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class FocusPhase {
    FOCUS,
    BREAK
}

data class FocusTimerState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val taskId: Long = 0L,
    val taskTitle: String = "",
    val phase: FocusPhase = FocusPhase.FOCUS,
    val secondsRemaining: Int = 25 * 60,
    val totalSeconds: Int = 25 * 60,
    val completedSessions: Int = 0
)

class FocusTimerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null

    companion object {
        const val CHANNEL_FOCUS = "vikunja_focus_channel"
        const val NOTIFICATION_ID = 9501

        const val ACTION_START = "cloud.codella.vikunja.focus.START"
        const val ACTION_PAUSE = "cloud.codella.vikunja.focus.PAUSE"
        const val ACTION_RESUME = "cloud.codella.vikunja.focus.RESUME"
        const val ACTION_STOP = "cloud.codella.vikunja.focus.STOP"

        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"

        private val _timerState = MutableStateFlow(FocusTimerState())
        val timerState = _timerState.asStateFlow()

        fun start(context: Context, taskId: Long, taskTitle: String) {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, taskTitle)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pause(context: Context) {
            val intent = Intent(context, FocusTimerService::class.java).apply { action = ACTION_PAUSE }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, FocusTimerService::class.java).apply { action = ACTION_RESUME }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusTimerService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
                val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Task Focus"
                startFocus(taskId, taskTitle)
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopFocus()
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_FOCUS,
                "Focus Session Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live focus countdown timer"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startFocus(taskId: Long, taskTitle: String) {
        val totalSec = 25 * 60
        _timerState.value = FocusTimerState(
            isRunning = true,
            isPaused = false,
            taskId = taskId,
            taskTitle = taskTitle,
            phase = FocusPhase.FOCUS,
            secondsRemaining = totalSec,
            totalSeconds = totalSec
        )
        startForeground(NOTIFICATION_ID, buildNotification())
        runTimerLoop()
    }

    private fun pauseTimer() {
        _timerState.value = _timerState.value.copy(isPaused = true)
        timerJob?.cancel()
        updateNotification()
    }

    private fun resumeTimer() {
        _timerState.value = _timerState.value.copy(isPaused = false)
        runTimerLoop()
    }

    private fun stopFocus() {
        timerJob?.cancel()
        _timerState.value = FocusTimerState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun runTimerLoop() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (_timerState.value.secondsRemaining > 0) {
                delay(1000)
                val current = _timerState.value
                val updatedSec = current.secondsRemaining - 1
                _timerState.value = current.copy(secondsRemaining = updatedSec)

                if (updatedSec % 5 == 0 || updatedSec <= 10) {
                    updateNotification()
                }
            }

            // Session completed! Switch phase
            val current = _timerState.value
            if (current.phase == FocusPhase.FOCUS) {
                // Completed focus session -> auto progress task if desired
                val breakSec = 5 * 60
                _timerState.value = current.copy(
                    phase = FocusPhase.BREAK,
                    secondsRemaining = breakSec,
                    totalSeconds = breakSec,
                    completedSessions = current.completedSessions + 1
                )
                updateNotification()
                runTimerLoop()
            } else {
                // Break ended -> ready for next focus
                val focusSec = 25 * 60
                _timerState.value = current.copy(
                    phase = FocusPhase.FOCUS,
                    secondsRemaining = focusSec,
                    totalSeconds = focusSec,
                    isPaused = true
                )
                updateNotification()
            }
        }
    }

    private fun buildNotification(): Notification {
        val state = _timerState.value
        val minutes = state.secondsRemaining / 60
        val seconds = state.secondsRemaining % 60
        val timeFormatted = String.format("%02d:%02d", minutes, seconds)

        val title = if (state.phase == FocusPhase.FOCUS) {
            "🍅 Focus: ${state.taskTitle}"
        } else {
            "☕ Short Break"
        }

        val content = if (state.isPaused) "Paused • $timeFormatted left" else "$timeFormatted remaining"

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_FOCUS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)

        if (state.isPaused) {
            val resumeIntent = Intent(this, FocusTimerService::class.java).apply { action = ACTION_RESUME }
            val resumePI = PendingIntent.getService(this, 1, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePI)
        } else {
            val pauseIntent = Intent(this, FocusTimerService::class.java).apply { action = ACTION_PAUSE }
            val pausePI = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePI)
        }

        val stopIntent = Intent(this, FocusTimerService::class.java).apply { action = ACTION_STOP }
        val stopPI = PendingIntent.getService(this, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPI)

        return builder.build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        timerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
