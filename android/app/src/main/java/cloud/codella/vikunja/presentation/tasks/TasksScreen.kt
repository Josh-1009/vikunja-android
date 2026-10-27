package cloud.codella.vikunja.presentation.tasks

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cloud.codella.vikunja.domain.model.Task
import cloud.codella.vikunja.presentation.navigation.ProjectDrawerContent
import cloud.codella.vikunja.presentation.tasks.components.QuickTaskInput
import cloud.codella.vikunja.presentation.tasks.components.TaskListItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onNavigateToDetail: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    initialQuickAddOpen: Boolean = false
) {
    val state by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    // Observe side effects
    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is TasksSideEffect.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = effect.actionLabel,
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        effect.onAction?.invoke()
                    }
                }
                is TasksSideEffect.TriggerHaptic -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                is TasksSideEffect.NavigateToDetail -> {
                    onNavigateToDetail(effect.taskId)
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ProjectDrawerContent(
                projects = state.allProjects,
                selectedSmartView = state.selectedSmartView,
                selectedProjectId = state.selectedProjectId,
                currentViewMode = state.viewMode,
                onSelectSmartView = { view ->
                    viewModel.onEvent(TasksUiEvent.SelectSmartView(view))
                    scope.launch { drawerState.close() }
                },
                onSelectProject = { id, title ->
                    viewModel.onEvent(TasksUiEvent.SelectProject(id, title))
                    scope.launch { drawerState.close() }
                },
                onSwitchViewMode = { mode ->
                    viewModel.onEvent(TasksUiEvent.SwitchViewMode(mode))
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TasksTopBar(
                    title = state.selectedProjectTitle,
                    isSyncing = state.isSyncing,
                    isSearchActive = state.isSearchActive,
                    searchQuery = state.searchQuery,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onRefreshSync = { viewModel.onEvent(TasksUiEvent.RefreshSync) },
                    onToggleSearch = { viewModel.onEvent(TasksUiEvent.ToggleSearch(it)) },
                    onSearchQueryChange = { viewModel.onEvent(TasksUiEvent.UpdateSearchQuery(it)) }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                QuickTaskInput(
                    onTaskSubmit = { cleanTitle, dueDateIso, priority, labels ->
                        viewModel.onEvent(
                            TasksUiEvent.CreateTaskFromDraft(
                                cleanTitle = cleanTitle,
                                dueDateIso = dueDateIso,
                                priority = priority,
                                labels = labels
                            )
                        )
                    },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding(),
                    autoFocus = initialQuickAddOpen
                )
            },
            modifier = modifier
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.isEmpty -> {
                        EmptyTasksState(
                            query = state.searchQuery,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    state.viewMode == ViewMode.LIST -> {
                        TasksListView(
                            state = state,
                            onToggleDone = { taskId, done ->
                                viewModel.onEvent(TasksUiEvent.ToggleTaskDone(taskId, done))
                            },
                            onDelete = { task ->
                                viewModel.onEvent(TasksUiEvent.DeleteTask(task))
                            },
                            onClick = { task ->
                                onNavigateToDetail(task.id)
                            }
                        )
                    }
                    state.viewMode == ViewMode.KANBAN -> {
                        TasksKanbanView(
                            state = state,
                            onToggleDone = { taskId, done ->
                                viewModel.onEvent(TasksUiEvent.ToggleTaskDone(taskId, done))
                            },
                            onDelete = { task ->
                                viewModel.onEvent(TasksUiEvent.DeleteTask(task))
                            },
                            onClick = { task ->
                                onNavigateToDetail(task.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksTopBar(
    title: String,
    isSyncing: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    onOpenDrawer: () -> Unit,
    onRefreshSync: () -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    val syncRotation by rememberInfiniteTransition(label = "sync_spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    if (isSearchActive) {
        TopAppBar(
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search tasks...") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = { onToggleSearch(false) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Close search")
                }
            },
            actions = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear query")
                    }
                }
            }
        )
    } else {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Open navigation drawer"
                        }
                ) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = null)
                }
            },
            actions = {
                IconButton(
                    onClick = { onToggleSearch(true) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search tasks")
                }
                IconButton(
                    onClick = onRefreshSync,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = if (isSyncing) "Syncing in background" else "Sync now",
                        modifier = if (isSyncing) Modifier.rotate(syncRotation) else Modifier
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TasksListView(
    state: TasksUiState,
    onToggleDone: (Long, Boolean) -> Unit,
    onDelete: (Task) -> Unit,
    onClick: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // 1. Overdue Section
        if (state.overdueTasks.isNotEmpty()) {
            stickyHeader(key = "header_overdue") {
                CategoryHeader(
                    title = "Overdue",
                    count = state.overdueTasks.size,
                    accentColor = Color(0xFFDC2626)
                )
            }
            items(
                items = state.overdueTasks,
                key = { it.id },
                contentType = { "task_item" }
            ) { task ->
                TaskListItem(
                    task = task,
                    onToggleDone = { done -> onToggleDone(task.id, done) },
                    onDelete = { onDelete(task) },
                    onClick = { onClick(task) },
                    modifier = Modifier.animateItemPlacement()
                )
            }
        }

        // 2. Today Section
        if (state.todayTasks.isNotEmpty()) {
            stickyHeader(key = "header_today") {
                CategoryHeader(
                    title = "Today",
                    count = state.todayTasks.size,
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }
            items(
                items = state.todayTasks,
                key = { it.id },
                contentType = { "task_item" }
            ) { task ->
                TaskListItem(
                    task = task,
                    onToggleDone = { done -> onToggleDone(task.id, done) },
                    onDelete = { onDelete(task) },
                    onClick = { onClick(task) },
                    modifier = Modifier.animateItemPlacement()
                )
            }
        }

        // 3. Upcoming Section
        if (state.upcomingTasks.isNotEmpty()) {
            stickyHeader(key = "header_upcoming") {
                CategoryHeader(
                    title = "Upcoming",
                    count = state.upcomingTasks.size,
                    accentColor = Color(0xFF2563EB)
                )
            }
            items(
                items = state.upcomingTasks,
                key = { it.id },
                contentType = { "task_item" }
            ) { task ->
                TaskListItem(
                    task = task,
                    onToggleDone = { done -> onToggleDone(task.id, done) },
                    onDelete = { onDelete(task) },
                    onClick = { onClick(task) },
                    modifier = Modifier.animateItemPlacement()
                )
            }
        }

        // 4. Completed Section
        if (state.completedTasks.isNotEmpty()) {
            stickyHeader(key = "header_completed") {
                CategoryHeader(
                    title = "Completed",
                    count = state.completedTasks.size,
                    accentColor = Color(0xFF16A34A)
                )
            }
            items(
                items = state.completedTasks,
                key = { it.id },
                contentType = { "task_item" }
            ) { task ->
                TaskListItem(
                    task = task,
                    onToggleDone = { done -> onToggleDone(task.id, done) },
                    onDelete = { onDelete(task) },
                    onClick = { onClick(task) },
                    modifier = Modifier.animateItemPlacement()
                )
            }
        }
    }
}

@Composable
private fun TasksKanbanView(
    state: TasksUiState,
    onToggleDone: (Long, Boolean) -> Unit,
    onDelete: (Task) -> Unit,
    onClick: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val columns = listOf(
        "Overdue" to state.overdueTasks,
        "Today" to state.todayTasks,
        "Upcoming" to state.upcomingTasks,
        "Completed" to state.completedTasks
    )

    LazyRow(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(columns, key = { it.first }) { (columnTitle, taskList) ->
            Surface(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .padding(bottom = 120.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = columnTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Badge { Text(taskList.size.toString()) }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = taskList,
                            key = { it.id },
                            contentType = { "task_item" }
                        ) { task ->
                            TaskListItem(
                                task = task,
                                onToggleDone = { done -> onToggleDone(task.id, done) },
                                onDelete = { onDelete(task) },
                                onClick = { onClick(task) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    title: String,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "($count)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun EmptyTasksState(
    query: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (query.isNotBlank()) Icons.Default.SearchOff else Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (query.isNotBlank()) "No tasks matching \"$query\"" else "All caught up!",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (query.isNotBlank()) "Try checking for typos or searching another term."
            else "You have no pending tasks in this view. Enjoy your day!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
