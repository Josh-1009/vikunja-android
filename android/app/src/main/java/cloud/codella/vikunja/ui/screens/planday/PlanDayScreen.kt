package cloud.codella.vikunja.ui.screens.planday

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.codella.vikunja.R
import cloud.codella.vikunja.core.database.entity.TaskEntity
import cloud.codella.vikunja.core.model.TaskCreateUpdateRequest
import cloud.codella.vikunja.core.util.DateUtils
import cloud.codella.vikunja.core.util.DescriptionUtils
import cloud.codella.vikunja.data.repository.TaskRepository
import cloud.codella.vikunja.ui.theme.getPriorityColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDayScreen(
    taskRepository: TaskRepository,
    onNavigateBack: () -> Unit
) {
    val overdueTasks by taskRepository.overdueTasksFlow.collectAsState(initial = emptyList())
    val todayTasks by taskRepository.todayTasksFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // Aggregate tasks to plan (overdue first, then today's tasks)
    val reviewQueue = remember(overdueTasks, todayTasks) {
        val list = mutableListOf<TaskEntity>()
        list.addAll(overdueTasks.filter { !it.done })
        list.addAll(todayTasks.filter { !it.done && it !in list })
        list
    }

    var currentIndex by remember { mutableStateOf(0) }
    val isComplete = reviewQueue.isEmpty() || currentIndex >= reviewQueue.size

    val isoFormat = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan My Day", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isComplete) {
                // Celebration Complete View
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFD1FAE5),
                        modifier = Modifier.size(88.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🎉", fontSize = 42.sp)
                        }
                    }
                    Text(
                        text = "You're All Set!",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Your tasks for today are organized and clear. Go crush your goals!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Start My Day")
                    }
                }
            } else {
                val currentTask = reviewQueue[currentIndex]
                val progress = (currentIndex.toFloat() + 1) / reviewQueue.size.toFloat()

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Progress Indicator
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Task ${currentIndex + 1} of ${reviewQueue.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    // Main Task Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Priority & Overdue Tag
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentTask.priority > 0) {
                                    val pColor = getPriorityColor(currentTask.priority)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = pColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Priority ${currentTask.priority}",
                                            color = pColor,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                if (currentTask.dueDate != null) {
                                    val dueMillis = DateUtils.parseIsoToMillis(currentTask.dueDate) ?: 0L
                                    val isOverdue = DateUtils.isOverdue(dueMillis)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isOverdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = if (isOverdue) "Overdue" else "Due Today",
                                            color = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = currentTask.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )

                            if (currentTask.description.isNotBlank()) {
                                val cleanDesc = remember(currentTask.description) { DescriptionUtils.cleanSnippet(currentTask.description) }
                                if (cleanDesc.isNotBlank()) {
                                    Text(
                                        text = cleanDesc,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Decision Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Keep for Today
                        Button(
                            onClick = { currentIndex++ },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Today, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Keep for Today")
                        }

                        // 2. Postpone to Tomorrow
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                                    val tomorrowIso = isoFormat.format(cal.time)
                                    taskRepository.updateTask(currentTask.id, TaskCreateUpdateRequest(title = currentTask.title, dueDate = tomorrowIso))
                                    currentIndex++
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.FastForward, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Postpone to Tomorrow")
                        }

                        // 3. Mark Done
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    taskRepository.toggleTaskDone(currentTask.id, false)
                                    currentIndex++
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Already Done")
                        }
                    }
                }
            }
        }
    }
}
