package cloud.codella.vikunja.ui.screens.project

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.codella.vikunja.R
import cloud.codella.vikunja.core.database.entity.ProjectEntity
import cloud.codella.vikunja.core.database.entity.TaskEntity
import cloud.codella.vikunja.core.model.Bucket
import cloud.codella.vikunja.core.model.ProjectView
import cloud.codella.vikunja.data.repository.ProjectRepository
import cloud.codella.vikunja.data.repository.TaskRepository
import cloud.codella.vikunja.data.repository.ViewRepository
import cloud.codella.vikunja.ui.screens.overview.VikunjaTaskCard
import cloud.codella.vikunja.ui.theme.parseProjectColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: Long,
    projectRepository: ProjectRepository,
    taskRepository: TaskRepository,
    viewRepository: ViewRepository,
    onNavigateBack: () -> Unit,
    onNavigateToTask: (Long) -> Unit,
    onOpenQuickAdd: () -> Unit
) {
    var selectedViewKind by remember { mutableStateOf("list") } // list, kanban, gantt, table
    var projectViews by remember { mutableStateOf<List<ProjectView>>(emptyList()) }
    var buckets by remember { mutableStateOf<List<Bucket>>(emptyList()) }
    var showAddBucketDialog by remember { mutableStateOf(false) }
    var newBucketTitle by remember { mutableStateOf("") }

    val projects by projectRepository.projectsFlow.collectAsState(initial = emptyList())
    val currentProject = remember(projects, projectId) { projects.firstOrNull { it.id == projectId } }
    val projectColor = remember(currentProject) { parseProjectColor(currentProject?.hexColor) }

    val projectTasks by taskRepository.getTasksByProjectFlow(projectId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    LaunchedEffect(projectId) {
        taskRepository.refreshTasks(projectId)
        val viewsResult = viewRepository.getViews(projectId)
        if (viewsResult.isSuccess) {
            projectViews = viewsResult.getOrDefault(emptyList())
            val kanbanView = projectViews.firstOrNull { it.viewKind == "kanban" }
            if (kanbanView != null) {
                val bRes = viewRepository.getBuckets(projectId, kanbanView.id)
                buckets = bRes.getOrDefault(emptyList())
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(projectColor)
                        )
                        Text(
                            text = currentProject?.title ?: stringResource(R.string.tasks),
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    // View Switcher Menu
                    var expandedMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { expandedMenu = true }) {
                        Icon(
                            imageVector = when (selectedViewKind) {
                                "kanban" -> Icons.Default.ViewKanban
                                "gantt" -> Icons.Default.Timeline
                                "table" -> Icons.Default.TableChart
                                else -> Icons.AutoMirrored.Filled.ViewList
                            },
                            contentDescription = "Switch View"
                        )
                    }
                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.view_list)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null) },
                            onClick = {
                                selectedViewKind = "list"
                                expandedMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.view_kanban)) },
                            leadingIcon = { Icon(Icons.Default.ViewKanban, contentDescription = null) },
                            onClick = {
                                selectedViewKind = "kanban"
                                expandedMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.view_gantt)) },
                            leadingIcon = { Icon(Icons.Default.Timeline, contentDescription = null) },
                            onClick = {
                                selectedViewKind = "gantt"
                                expandedMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.view_table)) },
                            leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                            onClick = {
                                selectedViewKind = "table"
                                expandedMenu = false
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenQuickAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.create_task), fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedViewKind) {
                "kanban" -> KanbanView(
                    buckets = buckets,
                    tasks = projectTasks,
                    onTaskClick = onNavigateToTask,
                    onAddBucketClick = { showAddBucketDialog = true },
                    onMoveTask = { bucketId, taskId ->
                        scope.launch {
                            val kanbanView = projectViews.firstOrNull { it.viewKind == "kanban" }
                            if (kanbanView != null) {
                                viewRepository.moveTaskToBucket(projectId, kanbanView.id, bucketId, taskId)
                                taskRepository.refreshTasks(projectId)
                            }
                        }
                    }
                )
                "gantt" -> GanttView(
                    tasks = projectTasks,
                    onTaskClick = onNavigateToTask
                )
                "table" -> TableView(
                    tasks = projectTasks,
                    onTaskClick = onNavigateToTask
                )
                else -> ProjectListView(
                    tasks = projectTasks,
                    project = currentProject,
                    onTaskClick = onNavigateToTask,
                    onToggleDone = { task ->
                        scope.launch { taskRepository.toggleTaskDone(task.id, task.done) }
                    },
                    onToggleFavorite = { task ->
                        scope.launch { taskRepository.toggleTaskFavorite(task.id, task.isFavorite) }
                    },
                    onOpenQuickAdd = onOpenQuickAdd
                )
            }
        }

        if (showAddBucketDialog) {
            AlertDialog(
                onDismissRequest = { showAddBucketDialog = false },
                title = { Text(stringResource(R.string.add_bucket)) },
                text = {
                    OutlinedTextField(
                        value = newBucketTitle,
                        onValueChange = { newBucketTitle = it },
                        label = { Text(stringResource(R.string.bucket_title)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newBucketTitle.isNotBlank()) {
                                scope.launch {
                                    val kanbanView = projectViews.firstOrNull { it.viewKind == "kanban" }
                                    if (kanbanView != null) {
                                        viewRepository.createBucket(projectId, kanbanView.id, newBucketTitle.trim())
                                        val bRes = viewRepository.getBuckets(projectId, kanbanView.id)
                                        buckets = bRes.getOrDefault(emptyList())
                                    }
                                    showAddBucketDialog = false
                                    newBucketTitle = ""
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddBucketDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun ProjectListView(
    tasks: List<TaskEntity>,
    project: ProjectEntity?,
    onTaskClick: (Long) -> Unit,
    onToggleDone: (TaskEntity) -> Unit,
    onToggleFavorite: (TaskEntity) -> Unit,
    onOpenQuickAdd: () -> Unit
) {
    val activeTasks = remember(tasks) { tasks.filter { !it.done } }
    val completedTasks = remember(tasks) { tasks.filter { it.done } }
    var isCompletedExpanded by remember { mutableStateOf(false) }

    if (tasks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Text(
                    text = "No tasks in this project yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FilledTonalButton(
                    onClick = onOpenQuickAdd,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.create_task))
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Active Tasks
            items(activeTasks, key = { it.id }) { task ->
                VikunjaTaskCard(
                    task = task,
                    project = project,
                    onClick = { onTaskClick(task.id) },
                    onToggleDone = { onToggleDone(task) },
                    onToggleFavorite = { onToggleFavorite(task) },
                    onReschedule = {}
                )
            }

            // Collapsible Completed Tasks Section (Web App Style)
            if (completedTasks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { isCompletedExpanded = !isCompletedExpanded },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.completed_tasks, completedTasks.size),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = if (isCompletedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (isCompletedExpanded) {
                    items(completedTasks, key = { it.id }) { task ->
                        VikunjaTaskCard(
                            task = task,
                            project = project,
                            onClick = { onTaskClick(task.id) },
                            onToggleDone = { onToggleDone(task) },
                            onToggleFavorite = { onToggleFavorite(task) },
                            onReschedule = {}
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KanbanView(
    buckets: List<Bucket>,
    tasks: List<TaskEntity>,
    onTaskClick: (Long) -> Unit,
    onAddBucketClick: () -> Unit,
    onMoveTask: (Long, Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        for (bucket in buckets) {
            val bucketTasks = tasks.filter { it.bucketId == bucket.id }

            Card(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bucket.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Badge {
                            Text("${bucketTasks.size}${if (bucket.limit > 0) "/${bucket.limit}" else ""}")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(bucketTasks, key = { it.id }) { task ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                onClick = { onTaskClick(task.id) }
                            ) {
                                Text(
                                    text = task.title,
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Bucket Button Column
        OutlinedButton(
            onClick = onAddBucketClick,
            modifier = Modifier
                .width(200.dp)
                .height(60.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_bucket))
        }
    }
}

@Composable
fun GanttView(
    tasks: List<TaskEntity>,
    onTaskClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tasks) { task ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                onClick = { onTaskClick(task.id) }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(task.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (task.percentDone / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Start: ${task.startDate?.take(10) ?: "None"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Due: ${task.dueDate?.take(10) ?: "None"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TableView(
    tasks: List<TaskEntity>,
    onTaskClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("#", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                Text("Title", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                Text("Priority", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Status", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            HorizontalDivider()
        }

        items(tasks) { task ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTaskClick(task.id) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${task.id}", modifier = Modifier.weight(0.5f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(task.title, modifier = Modifier.weight(2f), fontWeight = FontWeight.Medium)
                Text(if (task.priority > 0) "P${task.priority}" else "-", modifier = Modifier.weight(1f))
                Text(if (task.done) "Done" else "Active", modifier = Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}
