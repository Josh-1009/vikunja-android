package cloud.codella.vikunja.ui.screens.matrix

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.codella.vikunja.R
import cloud.codella.vikunja.core.database.entity.TaskEntity
import cloud.codella.vikunja.core.util.DateUtils
import cloud.codella.vikunja.data.repository.TaskRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixScreen(
    taskRepository: TaskRepository,
    onNavigateBack: () -> Unit,
    onNavigateToTask: (Long) -> Unit
) {
    val allTasks by taskRepository.allTasksFlow.collectAsState(initial = emptyList())
    val activeTasks = remember(allTasks) { allTasks.filter { !it.done } }
    val scope = rememberCoroutineScope()

    // Eisenhower Categorization
    // Q1: Urgent & Important: Priority >= 4 AND (isOverdue or isToday or dueDate within 3 days)
    val q1Tasks = remember(activeTasks) {
        activeTasks.filter { task ->
            val dueMillis = DateUtils.parseIsoToMillis(task.dueDate)
            val isUrgentTime = dueMillis != null && (DateUtils.isOverdue(dueMillis) || DateUtils.isToday(dueMillis))
            val isUrgentPriority = task.priority >= 4
            isUrgentPriority || (isUrgentTime && task.priority >= 3)
        }
    }

    // Q2: Important, Not Urgent: Priority >= 3 AND NOT in Q1
    val q2Tasks = remember(activeTasks, q1Tasks) {
        activeTasks.filter { task ->
            task !in q1Tasks && task.priority >= 3
        }
    }

    // Q3: Urgent, Not Important: Due soon (Today/Overdue) but priority < 3
    val q3Tasks = remember(activeTasks, q1Tasks, q2Tasks) {
        activeTasks.filter { task ->
            if (task in q1Tasks || task in q2Tasks) return@filter false
            val dueMillis = DateUtils.parseIsoToMillis(task.dueDate)
            dueMillis != null && (DateUtils.isOverdue(dueMillis) || DateUtils.isToday(dueMillis))
        }
    }

    // Q4: Neither: Remaining tasks
    val q4Tasks = remember(activeTasks, q1Tasks, q2Tasks, q3Tasks) {
        activeTasks.filter { task ->
            task !in q1Tasks && task !in q2Tasks && task !in q3Tasks
        }
    }

    var selectedQuadrant by remember { mutableStateOf(0) } // 0=All 4, 1=Q1, 2=Q2, 3=Q3, 4=Q4

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eisenhower Matrix", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Quadrant Filter Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedQuadrant,
                edgePadding = 0.dp,
                divider = {}
            ) {
                Tab(selected = selectedQuadrant == 0, onClick = { selectedQuadrant = 0 }, text = { Text("Grid 2×2") })
                Tab(selected = selectedQuadrant == 1, onClick = { selectedQuadrant = 1 }, text = { Text("Do First (${q1Tasks.size})") })
                Tab(selected = selectedQuadrant == 2, onClick = { selectedQuadrant = 2 }, text = { Text("Schedule (${q2Tasks.size})") })
                Tab(selected = selectedQuadrant == 3, onClick = { selectedQuadrant = 3 }, text = { Text("Delegate (${q3Tasks.size})") })
                Tab(selected = selectedQuadrant == 4, onClick = { selectedQuadrant = 4 }, text = { Text("Backlog (${q4Tasks.size})") })
            }

            if (selectedQuadrant == 0) {
                // 2x2 Quadrant Grid
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuadrantCard(
                            title = "DO FIRST",
                            subtitle = "Urgent & Important",
                            color = Color(0xFFEF4444),
                            tasks = q1Tasks,
                            modifier = Modifier.weight(1f),
                            onTaskClick = onNavigateToTask,
                            onToggleDone = { task -> scope.launch { taskRepository.toggleTaskDone(task.id, task.done) } }
                        )
                        QuadrantCard(
                            title = "SCHEDULE",
                            subtitle = "Not Urgent · Important",
                            color = Color(0xFF3B82F6),
                            tasks = q2Tasks,
                            modifier = Modifier.weight(1f),
                            onTaskClick = onNavigateToTask,
                            onToggleDone = { task -> scope.launch { taskRepository.toggleTaskDone(task.id, task.done) } }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuadrantCard(
                            title = "DELEGATE",
                            subtitle = "Urgent · Less Important",
                            color = Color(0xFFF59E0B),
                            tasks = q3Tasks,
                            modifier = Modifier.weight(1f),
                            onTaskClick = onNavigateToTask,
                            onToggleDone = { task -> scope.launch { taskRepository.toggleTaskDone(task.id, task.done) } }
                        )
                        QuadrantCard(
                            title = "BACKLOG",
                            subtitle = "Neither Urgent nor Imp",
                            color = Color(0xFF64748B),
                            tasks = q4Tasks,
                            modifier = Modifier.weight(1f),
                            onTaskClick = onNavigateToTask,
                            onToggleDone = { task -> scope.launch { taskRepository.toggleTaskDone(task.id, task.done) } }
                        )
                    }
                }
            } else {
                // Focused Single Quadrant View
                val (qTitle, qColor, targetTasks) = when (selectedQuadrant) {
                    1 -> Triple("DO FIRST (Urgent & Important)", Color(0xFFEF4444), q1Tasks)
                    2 -> Triple("SCHEDULE (Important, Not Urgent)", Color(0xFF3B82F6), q2Tasks)
                    3 -> Triple("DELEGATE (Urgent, Not Important)", Color(0xFFF59E0B), q3Tasks)
                    else -> Triple("BACKLOG / ELIMINATE", Color(0xFF64748B), q4Tasks)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = qColor.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, qColor.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = qTitle,
                        fontWeight = FontWeight.Bold,
                        color = qColor,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(targetTasks, key = { it.id }) { task ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToTask(task.id) },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.done,
                                    onCheckedChange = { scope.launch { taskRepository.toggleTaskDone(task.id, task.done) } }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuadrantCard(
    title: String,
    subtitle: String,
    color: Color,
    tasks: List<TaskEntity>,
    modifier: Modifier = Modifier,
    onTaskClick: (Long) -> Unit,
    onToggleDone: (TaskEntity) -> Unit
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = color
                )
                Badge(containerColor = color.copy(alpha = 0.15f)) {
                    Text("${tasks.size}", color = color, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = color.copy(alpha = 0.2f))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onTaskClick(task.id) },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
