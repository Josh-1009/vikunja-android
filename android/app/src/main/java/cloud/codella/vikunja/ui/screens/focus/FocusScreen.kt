package cloud.codella.vikunja.ui.screens.focus

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.codella.vikunja.R
import cloud.codella.vikunja.VikunjaApp
import cloud.codella.vikunja.core.database.entity.TaskEntity
import cloud.codella.vikunja.core.timer.PomodoroMode
import cloud.codella.vikunja.data.repository.TaskRepository
import cloud.codella.vikunja.service.FocusTaskNotificationService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    taskRepository: TaskRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val app = context.applicationContext as? VikunjaApp
    val pomodoroEngine = remember { app?.pomodoroEngine ?: cloud.codella.vikunja.core.timer.PomodoroEngine() }

    val timerState by pomodoroEngine.state.collectAsState()
    val allTasks by taskRepository.allTasksFlow.collectAsState(initial = emptyList())
    val activeTasks = remember(allTasks) { allTasks.filter { !it.done } }

    var selectedTask by remember { mutableStateOf<TaskEntity?>(null) }
    var showTaskPicker by remember { mutableStateOf(false) }

    val minutes = timerState.remainingSeconds / 60
    val seconds = timerState.remainingSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    val progress = if (timerState.totalSeconds > 0) {
        timerState.remainingSeconds.toFloat() / timerState.totalSeconds.toFloat()
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500),
        label = "pomodoro_progress"
    )

    // Dynamic color theme depending on mode
    val isWork = timerState.mode == PomodoroMode.WORK || timerState.mode == PomodoroMode.IDLE
    val activeColor by animateColorAsState(
        targetValue = when (timerState.mode) {
            PomodoroMode.WORK, PomodoroMode.IDLE -> MaterialTheme.colorScheme.primary
            PomodoroMode.SHORT_BREAK -> Color(0xFF10B981)
            PomodoroMode.LONG_BREAK -> Color(0xFF06B6D4)
        },
        label = "phase_color"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.focus_timer), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Task Association Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (timerState.taskTitle != null) "FOCUSING ON" else "SELECTED TASK",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = activeColor
                        )
                        Text(
                            text = timerState.taskTitle ?: selectedTask?.title ?: "Select a task to focus on...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }

                    if (!timerState.isRunning) {
                        FilledTonalButton(
                            onClick = { showTaskPicker = true },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Change")
                        }
                    }
                }
            }

            // Circular Countdown Display with Canvas Graphics
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(280.dp)
            ) {
                val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                val strokeWidth = 14.dp

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = strokeWidth.toPx()
                    val diameter = size.minDimension - strokePx
                    val topLeft = Offset(strokePx / 2f, strokePx / 2f)
                    val arcSize = Size(diameter, diameter)

                    // Background Track
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )

                    // Active Countdown Arc
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(activeColor.copy(alpha = 0.8f), activeColor)
                        ),
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Phase Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = activeColor.copy(alpha = 0.15f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = when (timerState.mode) {
                                PomodoroMode.WORK -> "🎯 WORK SESSION"
                                PomodoroMode.SHORT_BREAK -> "☕ SHORT BREAK"
                                PomodoroMode.LONG_BREAK -> "🌴 LONG BREAK"
                                PomodoroMode.IDLE -> "READY TO FOCUS"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = activeColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    // Digital Time Display
                    Text(
                        text = timeFormatted,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 2.sp
                    )

                    // Cycle dots / indicator
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        for (i in 1..4) {
                            val isCompleted = i < timerState.currentCycle
                            val isCurrent = i == timerState.currentCycle && timerState.mode == PomodoroMode.WORK
                            Box(
                                modifier = Modifier
                                    .size(if (isCurrent) 10.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isCompleted -> activeColor
                                            isCurrent -> activeColor
                                            else -> MaterialTheme.colorScheme.outlineVariant
                                        }
                                    )
                            )
                        }
                    }

                    Text(
                        text = "Pomodoro ${timerState.currentCycle} of 4 🍅",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset / Stop Button
                if (timerState.isRunning || timerState.isPaused) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pomodoroEngine.stop()
                            FocusTaskNotificationService.stop(context)
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Play / Pause Primary Button
                LargeFloatingActionButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!timerState.isRunning) {
                            val targetId = selectedTask?.id
                            val targetTitle = selectedTask?.title ?: "Deep Work"
                            pomodoroEngine.startWork(targetId, targetTitle)
                            FocusTaskNotificationService.start(context, targetId ?: 0L, targetTitle)
                        } else if (timerState.isPaused) {
                            pomodoroEngine.resume()
                        } else {
                            pomodoroEngine.pause()
                        }
                    },
                    containerColor = activeColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        imageVector = if (timerState.isRunning && !timerState.isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Timer",
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Skip to Next Phase Button
                if (timerState.isRunning || timerState.isPaused) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pomodoroEngine.skip()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Skip Phase", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Today's Stats Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${timerState.currentCycle - 1}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = activeColor
                        )
                        Text(
                            text = "Cycles Finished",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val totalMinutes = (timerState.currentCycle - 1) * 25
                        Text(
                            text = "${totalMinutes}m",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                        Text(
                            text = "Focused Today",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Task Selection Dialog / Sheet
    if (showTaskPicker) {
        AlertDialog(
            onDismissRequest = { showTaskPicker = false },
            title = { Text("Select Task for Focus", fontWeight = FontWeight.Bold) },
            text = {
                if (activeTasks.isEmpty()) {
                    Text("No active tasks available. Create a task first!")
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(activeTasks, key = { it.id }) { task ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTask = task
                                        showTaskPicker = false
                                    }
                                    .padding(vertical = 8.dp),
                                color = Color.Transparent
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircleOutline,
                                        contentDescription = null,
                                        tint = activeColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTaskPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
