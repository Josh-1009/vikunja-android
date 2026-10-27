package cloud.codella.vikunja.ui.screens.reminder

import android.app.DatePickerDialog
import android.app.KeyguardManager
import android.app.TimePickerDialog
import android.content.Context
import java.util.Calendar
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.codella.vikunja.R
import cloud.codella.vikunja.VikunjaApp
import cloud.codella.vikunja.core.model.TaskCreateUpdateRequest
import cloud.codella.vikunja.core.notifications.AlarmScheduler
import cloud.codella.vikunja.core.notifications.NotificationHelper
import cloud.codella.vikunja.ui.theme.VikunjaTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderPopupActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen and display over keyguard/lockscreen
        setupLockscreenFlags()

        val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, 0L)
        val title = intent.getStringExtra(NotificationHelper.EXTRA_TASK_TITLE) ?: getString(R.string.reminder_title)
        val isDueDate = intent.getBooleanExtra("is_due_date", true)
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, 0)

        // Dismiss the status bar notification if present
        if (notificationId != 0) {
            NotificationHelper(this).cancelNotification(notificationId)
        }

        playAlert()

        val app = application as? VikunjaApp
        val scheduler = AlarmScheduler(this)

        setContent {
            val themeMode by app?.preferencesManager?.themeModeFlow?.collectAsState(initial = "system") ?: remember { mutableStateOf("system") }

            VikunjaTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Bell Icon
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = if (isDueDate) stringResource(R.string.reminder_due_now) else stringResource(R.string.reminder_scheduled),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Snooze options
                                Text(
                                    text = stringResource(R.string.reminder_snooze),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            stopAlert()
                                            scheduler.scheduleExactAlarm(
                                                taskId,
                                                title,
                                                System.currentTimeMillis() + (10 * 60 * 1000),
                                                isDueDate = false
                                            )
                                            finish()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(stringResource(R.string.reminder_snooze_10m), fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            stopAlert()
                                            scheduler.scheduleExactAlarm(
                                                taskId,
                                                title,
                                                System.currentTimeMillis() + (60 * 60 * 1000),
                                                isDueDate = false
                                            )
                                            finish()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(stringResource(R.string.reminder_snooze_1h), fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            stopAlert()
                                            scheduler.scheduleExactAlarm(
                                                taskId,
                                                title,
                                                System.currentTimeMillis() + (24 * 60 * 60 * 1000),
                                                isDueDate = false
                                            )
                                            finish()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(stringResource(R.string.reminder_snooze_tomorrow), fontSize = 12.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = {
                                        val cal = Calendar.getInstance()
                                        DatePickerDialog(
                                            this@ReminderPopupActivity,
                                            { _, year, month, dayOfMonth ->
                                                TimePickerDialog(
                                                    this@ReminderPopupActivity,
                                                    { _, hourOfDay, minute ->
                                                        val chosenCal = Calendar.getInstance().apply {
                                                            set(Calendar.YEAR, year)
                                                            set(Calendar.MONTH, month)
                                                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                            set(Calendar.MINUTE, minute)
                                                            set(Calendar.SECOND, 0)
                                                        }
                                                        val targetMillis = chosenCal.timeInMillis
                                                        if (targetMillis > System.currentTimeMillis()) {
                                                            stopAlert()
                                                            scheduler.scheduleExactAlarm(
                                                                taskId,
                                                                title,
                                                                targetMillis,
                                                                isDueDate = false
                                                            )
                                                            finish()
                                                        }
                                                    },
                                                    cal.get(Calendar.HOUR_OF_DAY),
                                                    cal.get(Calendar.MINUTE),
                                                    false
                                                ).show()
                                            },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.reminder_snooze_custom), fontSize = 12.sp)
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Action Buttons: Mark Done and Dismiss
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            stopAlert()
                                            finish()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(stringResource(R.string.reminder_dismiss))
                                    }

                                    Button(
                                        onClick = {
                                            stopAlert()
                                            if (app != null && taskId != 0L) {
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    try {
                                                        app.database.taskDao().updateTaskDone(taskId, true)
                                                        app.apiService.updateTask(
                                                            taskId,
                                                            TaskCreateUpdateRequest(title = title, done = true)
                                                        )
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            }
                                            finish()
                                        },
                                        modifier = Modifier.weight(1.2f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.mark_done))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupLockscreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun playAlert() {
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(applicationContext, alertUri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 500, 300, 500)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 300, 500), -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlert() {
        try {
            ringtone?.stop()
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopAlert()
        super.onDestroy()
    }
}
