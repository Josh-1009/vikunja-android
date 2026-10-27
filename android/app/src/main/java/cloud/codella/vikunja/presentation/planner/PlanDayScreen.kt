package cloud.codella.vikunja.presentation.planner

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.codella.vikunja.domain.model.Task
import cloud.codella.vikunja.domain.repository.TaskRepository
import cloud.codella.vikunja.domain.usecase.GetMyDayTasksUseCase
import cloud.codella.vikunja.domain.usecase.ToggleMyDayUseCase
import cloud.codella.vikunja.ui.theme.getPriorityColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDayScreen(
    taskRepository: TaskRepository,
    getMyDayTasksUseCase: GetMyDayTasksUseCase,
    toggleMyDayUseCase: ToggleMyDayUseCase,
    onNavigateBack: () -> Unit,
    onNavigateToTask: (Long) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val todayDate = remember { LocalDate.now().toString() }
    val formattedDateHeader = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))
    }

    // My Day Tasks Flow
    val myDayTasks: List<Task> by remember(todayDate) {
        getMyDayTasksUseCase(todayDate)
    }.collectAsState(initial = emptyList())

    // All Tasks to compute Smart Suggestions
    val allTasks by taskRepository.getTasksFlow().collectAsState(initial = emptyList())

    // Smart Suggestions (Overdue, High Priority !4/!5, Due Today) that aren't already in My Day
    val suggestions = remember(allTasks, myDayTasks) {
        val myDayIds = myDayTasks.map { it.id }.toSet()
        val todayIsoPrefix = todayDate
        allTasks.filter { task ->
            !task.done && task.id !in myDayIds && (
                task.priority >= 4 ||
                task.dueDate?.startsWith(todayIsoPrefix) == true ||
                (task.dueDate != null && task.dueDate < todayIsoPrefix)
            )
        }
    }

    var isSuggestionsExpanded by remember { mutableStateOf(suggestions.isNotEmpty() && myDayTasks.isEmpty()) }

    // Completion metrics
    val totalMyDay = myDayTasks.size
    val completedMyDay = myDayTasks.count { it.done }
    val progress = if (totalMyDay > 0) completedMyDay.toFloat() / totalMyDay.toFloat() else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "myday_progress")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Day", fontWeight = FontWeight.Bold)
                        Text(
                            text = formattedDateHeader,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isSuggestionsExpanded = !isSuggestionsExpanded }) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = "Suggestions"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Motivational Progress Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                totalMyDay == 0 -> "Plan your focus for today"
                                completedMyDay == totalMyDay -> "All done for today! 🎉"
                                else -> "$completedMyDay of $totalMyDay completed 🚀"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (totalMyDay > 0) {
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (totalMyDay > 0) {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            // Smart Suggestions Tray (Expandable)
            AnimatedVisibility(
                visible = isSuggestionsExpanded && suggestions.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💡 Smart Suggestions (${suggestions.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            TextButton(onClick = { isSuggestionsExpanded = false }) {
                                Text("Hide", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = suggestions,
                                key = { it.id },
                                contentType = { "task_item" }
                            ) { task ->
                                SuggestionItem(
                                    task = task,
                                    onAdd = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        scope.launch {
                                            toggleMyDayUseCase(task.id, true, todayDate)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // My Day Planned Tasks List
            if (myDayTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("☀️", fontSize = 48.sp)
                        Text(
                            text = "No tasks in My Day yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap the suggestions above to select what matters today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isSuggestionsExpanded && suggestions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(onClick = { isSuggestionsExpanded = true }) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Show Suggestions (${suggestions.size})")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = myDayTasks,
                        key = { it.id },
                        contentType = { "task_item" }
                    ) { task ->
                        MyDayTaskItem(
                            task = task,
                            onToggleDone = { done ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
                                    taskRepository.toggleTaskDone(task.id, done)
                                }
                            },
                            onRemoveFromMyDay = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch {
                                    toggleMyDayUseCase(task.id, false, todayDate)
                                }
                            },
                            onClick = { onNavigateToTask(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    task: Task,
    onAdd: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.priority > 0 || task.dueDate != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.priority > 0) {
                            Text(
                                text = "P${task.priority}",
                                color = getPriorityColor(task.priority),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        task.dueDate?.let { due ->
                            Text(
                                text = "Due $due",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onAdd,
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add to My Day",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MyDayTaskItem(
    task: Task,
    onToggleDone: (Boolean) -> Unit,
    onRemoveFromMyDay: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.done,
                onCheckedChange = onToggleDone,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF16A34A),
                    checkmarkColor = Color.White
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (task.done) FontWeight.Normal else FontWeight.Medium,
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.dueDate != null || task.priority > 0) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (task.priority > 0) {
                            Text(
                                text = "P${task.priority}",
                                color = getPriorityColor(task.priority),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        task.dueDate?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onRemoveFromMyDay) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove from My Day",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
