package cloud.codella.vikunja.ui.screens.overview

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.codella.vikunja.R
import cloud.codella.vikunja.core.database.entity.ProjectEntity
import cloud.codella.vikunja.core.database.entity.TaskEntity
import cloud.codella.vikunja.core.datastore.PreferencesManager
import cloud.codella.vikunja.core.focus.FocusTimerService
import cloud.codella.vikunja.core.model.ProjectCreateUpdateRequest
import cloud.codella.vikunja.core.model.TaskCreateUpdateRequest
import cloud.codella.vikunja.core.util.DateUtils
import cloud.codella.vikunja.core.util.DescriptionUtils
import cloud.codella.vikunja.core.util.GoogleCalendarHelper
import cloud.codella.vikunja.data.repository.ProjectRepository
import cloud.codella.vikunja.data.repository.TaskRepository
import cloud.codella.vikunja.ui.theme.VikunjaBlue
import cloud.codella.vikunja.ui.theme.getPriorityColor
import cloud.codella.vikunja.ui.theme.parseProjectColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class VikunjaView {
    MY_TASKS,
    ALL,
    OPEN,
    TODAY,
    SCHEDULE,
    COMPLETED,
    PROJECT,
    OVERDUE,
    STARRED
}

enum class TaskSortOrder {
    DEFAULT,
    DUE_DATE_ASC,
    DUE_DATE_DESC,
    PRIORITY_DESC,
    PRIORITY_ASC,
    TITLE_ASC,
    TITLE_DESC,
    CREATED_DESC,
    COMPLETED_DESC
}

enum class CompletedTimeFilter {
    ALL,
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    CUSTOM
}

@Composable
private fun FilterPill(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badge: String? = null,
    accentColor: Color? = null,
    isError: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primary
        isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) contentColor else (accentColor ?: contentColor.copy(alpha = 0.7f)),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    taskRepository: TaskRepository,
    projectRepository: ProjectRepository,
    preferencesManager: PreferencesManager? = null,
    onNavigateToProject: (Long) -> Unit,
    onNavigateToTask: (Long) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToFocus: () -> Unit = {},
    onNavigateToMatrix: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToHabits: () -> Unit = {},
    onNavigateToPlanDay: () -> Unit = {},
    onOpenQuickAdd: () -> Unit,
    onLoggedOut: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var currentView by remember { mutableStateOf(VikunjaView.MY_TASKS) }
    var selectedProjectId by remember { mutableStateOf<Long?>(null) }
    var showTitleDropdown by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedFilterChip by remember { mutableStateOf(0) } // 0=All, 1=Pending, 2=High Priority, 3=Has Due Date

    var sortOrder by remember { mutableStateOf(TaskSortOrder.DEFAULT) }
    var showSortDialog by remember { mutableStateOf(false) }

    var completedTimeFilter by remember { mutableStateOf(CompletedTimeFilter.ALL) }
    var customCompletedDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    var isRefreshing by remember { mutableStateOf(false) }
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var showSessionExpiredDialog by remember { mutableStateOf(false) }
    var rescheduleTargetTask by remember { mutableStateOf<TaskEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val allTasks by taskRepository.allTasksFlow.collectAsState(initial = emptyList())
    val todayTasks by taskRepository.todayTasksFlow.collectAsState(initial = emptyList())
    val upcomingTasks by taskRepository.upcomingTasksFlow.collectAsState(initial = emptyList())
    val overdueTasks by taskRepository.overdueTasksFlow.collectAsState(initial = emptyList())
    val starredTasks by taskRepository.starredTasksFlow.collectAsState(initial = emptyList())
    val projects by projectRepository.projectsFlow.collectAsState(initial = emptyList())

    val myTasksProject = remember(projects) {
        projects.firstOrNull { it.title.equals("My Tasks", ignoreCase = true) }
            ?: projects.firstOrNull { it.title.contains("Tasks", ignoreCase = true) }
            ?: projects.firstOrNull()
    }

    val activeUserId by (preferencesManager?.activeUserIdFlow?.collectAsState(initial = 0L) ?: remember { mutableStateOf(0L) })
    val activeUsername by (preferencesManager?.activeUsernameFlow?.collectAsState(initial = "") ?: remember { mutableStateOf("") })
    val serverUrl by (preferencesManager?.serverUrlFlow?.collectAsState(initial = "tasks.codella.cloud") ?: remember { mutableStateOf("tasks.codella.cloud") })

    val myTasksList = remember(allTasks, activeUserId, activeUsername, myTasksProject) {
        allTasks.filter { task ->
            val isCreatedByMe = (activeUserId > 0L && task.createdById == activeUserId) ||
                    (activeUsername.isNotBlank() && task.createdByUsername.equals(activeUsername, ignoreCase = true))
            val isAssignedToMe = (activeUserId > 0L && task.assigneeIds.split(",").map { it.trim() }.contains(activeUserId.toString())) ||
                    (activeUsername.isNotBlank() && task.assigneeUsernames.split(",").any { it.trim().equals(activeUsername, ignoreCase = true) })
            val isInMyTasksProject = myTasksProject != null && task.projectId == myTasksProject.id
            val hasNoAssigneeOrCreator = task.createdById == 0L && task.assigneeIds.isBlank()

            isCreatedByMe || isAssignedToMe || isInMyTasksProject || hasNoAssigneeOrCreator
        }
    }

    val completedTasksFiltered = remember(allTasks, completedTimeFilter, customCompletedDateMillis) {
        allTasks.filter { task ->
            if (!task.done) return@filter false
            val doneTime = (task.doneAt?.takeIf { it.isNotBlank() } ?: task.updated)?.let { DateUtils.parseIsoToMillis(it) }
            when (completedTimeFilter) {
                CompletedTimeFilter.ALL -> true
                CompletedTimeFilter.TODAY -> doneTime != null && DateUtils.isToday(doneTime)
                CompletedTimeFilter.YESTERDAY -> doneTime != null && DateUtils.isYesterday(doneTime)
                CompletedTimeFilter.THIS_WEEK -> doneTime != null && DateUtils.isThisWeek(doneTime)
                CompletedTimeFilter.THIS_MONTH -> doneTime != null && DateUtils.isThisMonth(doneTime)
                CompletedTimeFilter.CUSTOM -> {
                    if (customCompletedDateMillis == null || doneTime == null) false
                    else DateUtils.isSameDay(doneTime, customCompletedDateMillis!!)
                }
            }
        }
    }

    val cleanDomain = remember(serverUrl) {
        serverUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
    }

    val timerState by FocusTimerService.timerState.collectAsState()
    val isoFormat = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") } }

    LaunchedEffect(Unit) {
        launch {
            val pRes = projectRepository.refreshProjects()
            val ex = pRes.exceptionOrNull()
            if (ex is retrofit2.HttpException && ex.code() == 401) {
                showSessionExpiredDialog = true
            }
        }
        launch { taskRepository.refreshTasks() }
    }

    if (showCreateProjectDialog) {
        var projectTitle by remember { mutableStateOf("") }
        var projectDesc by remember { mutableStateOf("") }
        var isCreating by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isCreating) showCreateProjectDialog = false },
            title = { Text(stringResource(R.string.create_project), fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = projectTitle,
                        onValueChange = { projectTitle = it },
                        label = { Text(stringResource(R.string.task_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = projectDesc,
                        onValueChange = { projectDesc = it },
                        label = { Text(stringResource(R.string.task_description)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (projectTitle.isNotBlank()) {
                            isCreating = true
                            scope.launch {
                                val res = projectRepository.createProject(
                                    ProjectCreateUpdateRequest(
                                        title = projectTitle.trim(),
                                        description = projectDesc.trim(),
                                        hexColor = "1973E8"
                                    )
                                )
                                isCreating = false
                                showCreateProjectDialog = false
                                if (res.isFailure) {
                                    snackbarHostState.showSnackbar(res.exceptionOrNull()?.localizedMessage ?: "Failed to create project")
                                } else {
                                    projectRepository.refreshProjects()
                                }
                            }
                        }
                    },
                    enabled = projectTitle.isNotBlank() && !isCreating
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateProjectDialog = false },
                    enabled = !isCreating
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Reschedule Bottom Sheet Dialog
    if (rescheduleTargetTask != null) {
        AlertDialog(
            onDismissRequest = { rescheduleTargetTask = null },
            title = { Text("Reschedule Task", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }
                                scope.launch {
                                    taskRepository.updateTask(rescheduleTargetTask!!.id, TaskCreateUpdateRequest(title = rescheduleTargetTask!!.title, dueDate = isoFormat.format(cal.time)))
                                    rescheduleTargetTask = null
                                }
                            }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("☀️", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Today", fontWeight = FontWeight.Medium)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }
                                scope.launch {
                                    taskRepository.updateTask(rescheduleTargetTask!!.id, TaskCreateUpdateRequest(title = rescheduleTargetTask!!.title, dueDate = isoFormat.format(cal.time)))
                                    rescheduleTargetTask = null
                                }
                            }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🌅", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Tomorrow", fontWeight = FontWeight.Medium)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cal = Calendar.getInstance().apply { add(Calendar.WEEK_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59) }
                                scope.launch {
                                    taskRepository.updateTask(rescheduleTargetTask!!.id, TaskCreateUpdateRequest(title = rescheduleTargetTask!!.title, dueDate = isoFormat.format(cal.time)))
                                    rescheduleTargetTask = null
                                }
                            }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📅", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Next Week", fontWeight = FontWeight.Medium)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val target = rescheduleTargetTask ?: return@clickable
                                val pickerCal = Calendar.getInstance()
                                DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                val chosenCal = Calendar.getInstance().apply {
                                                    set(Calendar.YEAR, year)
                                                    set(Calendar.MONTH, month)
                                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                    set(Calendar.MINUTE, minute)
                                                    set(Calendar.SECOND, 0)
                                                }
                                                scope.launch {
                                                    taskRepository.updateTask(
                                                        target.id,
                                                        TaskCreateUpdateRequest(
                                                            title = target.title,
                                                            dueDate = isoFormat.format(chosenCal.time)
                                                        )
                                                    )
                                                    rescheduleTargetTask = null
                                                }
                                            },
                                            pickerCal.get(Calendar.HOUR_OF_DAY),
                                            pickerCal.get(Calendar.MINUTE),
                                            false
                                        ).show()
                                    },
                                    pickerCal.get(Calendar.YEAR),
                                    pickerCal.get(Calendar.MONTH),
                                    pickerCal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⏰", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(stringResource(R.string.reschedule_custom_datetime), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { rescheduleTargetTask = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSessionExpiredDialog) {
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Session Expired (401)") },
            text = {
                Text("Your login session has expired. Please log in again to continue.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSessionExpiredDialog = false
                        onLoggedOut()
                    }
                ) {
                    Text(stringResource(R.string.login))
                }
            }
        )
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text(stringResource(R.string.sort_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val sortOptions = listOf(
                        TaskSortOrder.DEFAULT to "Default",
                        TaskSortOrder.DUE_DATE_ASC to stringResource(R.string.sort_due_date_asc),
                        TaskSortOrder.DUE_DATE_DESC to stringResource(R.string.sort_due_date_desc),
                        TaskSortOrder.PRIORITY_DESC to stringResource(R.string.sort_priority_desc),
                        TaskSortOrder.PRIORITY_ASC to stringResource(R.string.sort_priority_asc),
                        TaskSortOrder.TITLE_ASC to stringResource(R.string.sort_title_asc),
                        TaskSortOrder.TITLE_DESC to stringResource(R.string.sort_title_desc),
                        TaskSortOrder.CREATED_DESC to stringResource(R.string.sort_created_desc),
                        TaskSortOrder.COMPLETED_DESC to stringResource(R.string.sort_completed_desc)
                    )

                    sortOptions.forEach { (order, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sortOrder = order
                                    showSortDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (sortOrder == order),
                                onClick = {
                                    sortOrder = order
                                    showSortDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = customCompletedDateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        customCompletedDateMillis = datePickerState.selectedDateMillis
                        completedTimeFilter = CompletedTimeFilter.CUSTOM
                        showDatePickerDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                // Vikunja Drawer Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = cleanDomain,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (activeUsername.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = activeUsername.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            Text(
                                text = activeUsername,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Scrollable Navigation Items
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Smart Lists Section
                    item {
                        val count = myTasksList.count { !it.done }
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            label = { Text(stringResource(R.string.nav_my_tasks), fontWeight = FontWeight.SemiBold) },
                            badge = {
                                if (count > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("$count", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            },
                            selected = currentView == VikunjaView.MY_TASKS,
                            onClick = {
                                currentView = VikunjaView.MY_TASKS
                                selectedProjectId = null
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item {
                        val count = allTasks.count { !it.done }
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
                            label = { Text(stringResource(R.string.nav_all_tasks), fontWeight = FontWeight.Medium) },
                            badge = {
                                if (count > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text("$count")
                                    }
                                }
                            },
                            selected = currentView == VikunjaView.ALL,
                            onClick = {
                                currentView = VikunjaView.ALL
                                selectedProjectId = null
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item {
                        val count = allTasks.count { !it.done }
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            label = { Text(stringResource(R.string.nav_open), fontWeight = FontWeight.Medium) },
                            badge = {
                                if (count > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("$count", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            },
                            selected = currentView == VikunjaView.OPEN,
                            onClick = {
                                currentView = VikunjaView.OPEN
                                selectedProjectId = null
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item {
                        val count = todayTasks.count { !it.done }
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Today, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            label = { Text(stringResource(R.string.nav_today), fontWeight = FontWeight.Medium) },
                            badge = {
                                if (count > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                        Text("$count", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            },
                            selected = currentView == VikunjaView.TODAY,
                            onClick = {
                                currentView = VikunjaView.TODAY
                                selectedProjectId = null
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item {
                        val count = allTasks.count { it.dueDate != null && !it.done }
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF0284C7)) },
                            label = { Text(stringResource(R.string.nav_schedule), fontWeight = FontWeight.Medium) },
                            badge = {
                                if (count > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text("$count")
                                    }
                                }
                            },
                            selected = currentView == VikunjaView.SCHEDULE,
                            onClick = {
                                currentView = VikunjaView.SCHEDULE
                                selectedProjectId = null
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item {
                        val count = allTasks.count { it.done }
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color(0xFF10B981)) },
                            label = { Text(stringResource(R.string.nav_completed), fontWeight = FontWeight.Medium) },
                            badge = {
                                if (count > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text("$count")
                                    }
                                }
                            },
                            selected = currentView == VikunjaView.COMPLETED,
                            onClick = {
                                currentView = VikunjaView.COMPLETED
                                selectedProjectId = null
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item {
                        val overdueCount = overdueTasks.count { !it.done }
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (overdueCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = { Text(stringResource(R.string.nav_overdue), fontWeight = FontWeight.Medium) },
                            badge = {
                                if (overdueCount > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text("$overdueCount", color = MaterialTheme.colorScheme.onError)
                                    }
                                }
                            },
                            selected = currentView == VikunjaView.OVERDUE,
                            onClick = {
                                currentView = VikunjaView.OVERDUE
                                selectedProjectId = null
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item {
                        val count = starredTasks.count { !it.done }
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFEAB308)) },
                            label = { Text(stringResource(R.string.nav_starred), fontWeight = FontWeight.Medium) },
                            badge = {
                                if (count > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                        Text("$count")
                                    }
                                }
                            },
                            selected = currentView == VikunjaView.STARRED,
                            onClick = {
                                currentView = VikunjaView.STARRED
                                selectedProjectId = null
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    // TickTick-Inspired Power Tools Section
                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "PRODUCTIVITY TOOLS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            label = { Text("Focus Timer (Pomodoro)", fontWeight = FontWeight.Medium) },
                            badge = {
                                if (timerState.isRunning) {
                                    Badge(containerColor = Color(0xFF10B981)) { Text("RUNNING") }
                                }
                            },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToFocus()
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.GridView, contentDescription = null, tint = Color(0xFF8B5CF6)) },
                            label = { Text("Eisenhower Matrix", fontWeight = FontWeight.Medium) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToMatrix()
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF0284C7)) },
                            label = { Text("Calendar & Schedule", fontWeight = FontWeight.Medium) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToCalendar()
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFF59E0B)) },
                            label = { Text("Habit Tracker & Streaks", fontWeight = FontWeight.Medium) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToHabits()
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.WbSunny, contentDescription = null, tint = Color(0xFFEAB308)) },
                            label = { Text("Plan My Day", fontWeight = FontWeight.Medium) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToPlanDay()
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }

                    // Projects Header with "+" button
                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.projects_header).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    showCreateProjectDialog = true
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.create_project),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Project Items
                    items(projects, key = { it.id }) { project ->
                        val projectColor = parseProjectColor(project.hexColor)
                        val projectTaskCount = allTasks.count { it.projectId == project.id && !it.done }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    scope.launch { drawerState.close() }
                                    onNavigateToProject(project.id)
                                },
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(projectColor)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = project.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (projectTaskCount > 0) {
                                    Text(
                                        text = "$projectTaskCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Drawer Footer Actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_notifications)) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToNotifications()
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.nav_settings)) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToSettings()
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        label = { Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onLoggedOut()
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    val viewTitle = when (currentView) {
                        VikunjaView.MY_TASKS -> stringResource(R.string.nav_my_tasks)
                        VikunjaView.ALL -> stringResource(R.string.nav_all_tasks)
                        VikunjaView.OPEN -> stringResource(R.string.nav_open)
                        VikunjaView.TODAY -> stringResource(R.string.nav_today)
                        VikunjaView.SCHEDULE -> stringResource(R.string.nav_schedule)
                        VikunjaView.COMPLETED -> stringResource(R.string.nav_completed)
                        VikunjaView.PROJECT -> projects.find { it.id == selectedProjectId }?.title ?: stringResource(R.string.projects_header)
                        VikunjaView.OVERDUE -> stringResource(R.string.nav_overdue)
                        VikunjaView.STARRED -> stringResource(R.string.nav_starred)
                    }

                    val viewIcon = when (currentView) {
                        VikunjaView.MY_TASKS -> Icons.Default.TaskAlt
                        VikunjaView.ALL -> Icons.Default.CheckCircleOutline
                        VikunjaView.OPEN -> Icons.Default.RadioButtonUnchecked
                        VikunjaView.TODAY -> Icons.Default.Today
                        VikunjaView.SCHEDULE -> Icons.Default.CalendarMonth
                        VikunjaView.COMPLETED -> Icons.Default.DoneAll
                        VikunjaView.PROJECT -> Icons.Default.Folder
                        VikunjaView.OVERDUE -> Icons.Default.Warning
                        VikunjaView.STARRED -> Icons.Default.Star
                    }

                    TopAppBar(
                        title = {
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showTitleDropdown = true }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = viewIcon,
                                        contentDescription = null,
                                        tint = if (currentView == VikunjaView.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = viewTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Switch view",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                DropdownMenu(
                                    expanded = showTitleDropdown,
                                    onDismissRequest = { showTitleDropdown = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("My Tasks (${myTasksList.count { !it.done }})") },
                                        leadingIcon = { Icon(Icons.Default.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            currentView = VikunjaView.MY_TASKS
                                            selectedProjectId = null
                                            showTitleDropdown = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("All Tasks (${allTasks.count { !it.done }})") },
                                        leadingIcon = { Icon(Icons.Default.CheckCircleOutline, contentDescription = null) },
                                        onClick = {
                                            currentView = VikunjaView.ALL
                                            selectedProjectId = null
                                            showTitleDropdown = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Open (${allTasks.count { !it.done }})") },
                                        leadingIcon = { Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null) },
                                        onClick = {
                                            currentView = VikunjaView.OPEN
                                            selectedProjectId = null
                                            showTitleDropdown = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Today (${todayTasks.count { !it.done }})") },
                                        leadingIcon = { Icon(Icons.Default.Today, contentDescription = null) },
                                        onClick = {
                                            currentView = VikunjaView.TODAY
                                            selectedProjectId = null
                                            showTitleDropdown = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Schedule (${allTasks.count { it.dueDate != null && !it.done }})") },
                                        leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                                        onClick = {
                                            currentView = VikunjaView.SCHEDULE
                                            selectedProjectId = null
                                            showTitleDropdown = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Completed (${allTasks.count { it.done }})") },
                                        leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null, tint = Color(0xFF10B981)) },
                                        onClick = {
                                            currentView = VikunjaView.COMPLETED
                                            selectedProjectId = null
                                            showTitleDropdown = false
                                        }
                                    )
                                    HorizontalDivider()
                                    projects.forEach { proj ->
                                        DropdownMenuItem(
                                            text = { Text("${proj.title} (${allTasks.count { it.projectId == proj.id && !it.done }})") },
                                            leadingIcon = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(parseProjectColor(proj.hexColor))
                                                )
                                            },
                                            onClick = {
                                                selectedProjectId = proj.id
                                                currentView = VikunjaView.PROJECT
                                                showTitleDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            val overdueCount = overdueTasks.count { !it.done }
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                BadgedBox(
                                    badge = {
                                        if (overdueCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                                            )
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                                }
                            }
                        },
                        actions = {
                            // Sort action
                            IconButton(onClick = { showSortDialog = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.sort_title),
                                    tint = if (sortOrder != TaskSortOrder.DEFAULT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Quick Calendar Shortcut
                            IconButton(onClick = onNavigateToCalendar) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar")
                            }

                            // Search Toggle
                            IconButton(onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            }) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search"
                                )
                            }

                            // Refresh
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        isRefreshing = true
                                        projectRepository.refreshProjects()
                                        taskRepository.refreshTasks()
                                        isRefreshing = false
                                    }
                                },
                                enabled = !isRefreshing
                            ) {
                                if (isRefreshing) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                }
                            }
                        }
                    )

                    // Primary Smart-View Tabs / Pills Row (1-Tap Switching!)
                    val myTasksActiveCount = myTasksList.count { !it.done }
                    val allActiveCount = allTasks.count { !it.done }
                    val openActiveCount = allTasks.count { !it.done }
                    val todayActiveCount = todayTasks.count { !it.done }
                    val scheduleActiveCount = allTasks.count { it.dueDate != null && !it.done }
                    val completedCount = allTasks.count { it.done }
                    val overdueActiveCount = overdueTasks.count { !it.done }
                    val starredActiveCount = starredTasks.count { !it.done }

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterPill(
                                selected = currentView == VikunjaView.MY_TASKS,
                                label = stringResource(R.string.nav_my_tasks),
                                icon = Icons.Default.TaskAlt,
                                badge = if (myTasksActiveCount > 0) "$myTasksActiveCount" else null,
                                onClick = {
                                    currentView = VikunjaView.MY_TASKS
                                    selectedProjectId = null
                                }
                            )
                        }
                        item {
                            FilterPill(
                                selected = currentView == VikunjaView.ALL,
                                label = stringResource(R.string.nav_all_tasks),
                                icon = Icons.Default.CheckCircleOutline,
                                badge = if (allActiveCount > 0) "$allActiveCount" else null,
                                onClick = {
                                    currentView = VikunjaView.ALL
                                    selectedProjectId = null
                                }
                            )
                        }
                        item {
                            FilterPill(
                                selected = currentView == VikunjaView.OPEN,
                                label = stringResource(R.string.nav_open),
                                icon = Icons.Default.RadioButtonUnchecked,
                                badge = if (openActiveCount > 0) "$openActiveCount" else null,
                                onClick = {
                                    currentView = VikunjaView.OPEN
                                    selectedProjectId = null
                                }
                            )
                        }
                        item {
                            FilterPill(
                                selected = currentView == VikunjaView.TODAY,
                                label = stringResource(R.string.nav_today),
                                icon = Icons.Default.Today,
                                badge = if (todayActiveCount > 0) "$todayActiveCount" else null,
                                onClick = {
                                    currentView = VikunjaView.TODAY
                                    selectedProjectId = null
                                }
                            )
                        }
                        item {
                            FilterPill(
                                selected = currentView == VikunjaView.SCHEDULE,
                                label = stringResource(R.string.nav_schedule),
                                icon = Icons.Default.CalendarMonth,
                                badge = if (scheduleActiveCount > 0) "$scheduleActiveCount" else null,
                                onClick = {
                                    currentView = VikunjaView.SCHEDULE
                                    selectedProjectId = null
                                }
                            )
                        }
                        item {
                            FilterPill(
                                selected = currentView == VikunjaView.COMPLETED,
                                label = stringResource(R.string.nav_completed),
                                icon = Icons.Default.DoneAll,
                                badge = if (completedCount > 0) "$completedCount" else null,
                                accentColor = Color(0xFF10B981),
                                onClick = {
                                    currentView = VikunjaView.COMPLETED
                                    selectedProjectId = null
                                }
                            )
                        }
                        projects.forEach { proj ->
                            item(key = "proj_${proj.id}") {
                                val pCount = allTasks.count { it.projectId == proj.id && !it.done }
                                FilterPill(
                                    selected = currentView == VikunjaView.PROJECT && selectedProjectId == proj.id,
                                    label = proj.title,
                                    icon = Icons.Default.Folder,
                                    badge = if (pCount > 0) "$pCount" else null,
                                    accentColor = parseProjectColor(proj.hexColor),
                                    onClick = {
                                        selectedProjectId = proj.id
                                        currentView = VikunjaView.PROJECT
                                    }
                                )
                            }
                        }
                        item {
                            FilterPill(
                                selected = currentView == VikunjaView.STARRED,
                                label = stringResource(R.string.nav_starred),
                                icon = Icons.Default.Star,
                                badge = if (starredActiveCount > 0) "$starredActiveCount" else null,
                                accentColor = Color(0xFFEAB308),
                                onClick = {
                                    currentView = VikunjaView.STARRED
                                    selectedProjectId = null
                                }
                            )
                        }
                        if (overdueActiveCount > 0) {
                            item {
                                FilterPill(
                                    selected = currentView == VikunjaView.OVERDUE,
                                    label = stringResource(R.string.nav_overdue),
                                    icon = Icons.Default.Warning,
                                    badge = "$overdueActiveCount",
                                    isError = true,
                                    onClick = {
                                        currentView = VikunjaView.OVERDUE
                                        selectedProjectId = null
                                    }
                                )
                            }
                        }
                    }

                    // Completed Time Filter Chips Row (shown when Completed view is active)
                    if (currentView == VikunjaView.COMPLETED) {
                        val displayCustomDate = remember(customCompletedDateMillis) {
                            if (customCompletedDateMillis != null) {
                                SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(customCompletedDateMillis!!))
                            } else null
                        }

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = completedTimeFilter == CompletedTimeFilter.ALL,
                                    onClick = { completedTimeFilter = CompletedTimeFilter.ALL },
                                    label = { Text(stringResource(R.string.time_filter_all)) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = completedTimeFilter == CompletedTimeFilter.TODAY,
                                    onClick = { completedTimeFilter = CompletedTimeFilter.TODAY },
                                    label = { Text(stringResource(R.string.time_filter_today)) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = completedTimeFilter == CompletedTimeFilter.YESTERDAY,
                                    onClick = { completedTimeFilter = CompletedTimeFilter.YESTERDAY },
                                    label = { Text(stringResource(R.string.time_filter_yesterday)) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = completedTimeFilter == CompletedTimeFilter.THIS_WEEK,
                                    onClick = { completedTimeFilter = CompletedTimeFilter.THIS_WEEK },
                                    label = { Text(stringResource(R.string.time_filter_this_week)) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = completedTimeFilter == CompletedTimeFilter.THIS_MONTH,
                                    onClick = { completedTimeFilter = CompletedTimeFilter.THIS_MONTH },
                                    label = { Text(stringResource(R.string.time_filter_this_month)) }
                                )
                            }
                            item {
                                FilterChip(
                                    selected = completedTimeFilter == CompletedTimeFilter.CUSTOM,
                                    onClick = { showDatePickerDialog = true },
                                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text(if (completedTimeFilter == CompletedTimeFilter.CUSTOM && displayCustomDate != null) displayCustomDate else stringResource(R.string.time_filter_custom)) }
                                )
                            }
                        }
                    }

                    // Schedule Jump-to-Calendar Interactive Banner
                    if (currentView == VikunjaView.SCHEDULE) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable(onClick = onNavigateToCalendar),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text(stringResource(R.string.open_calendar_view), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Expandable Live Search Field
                    AnimatedVisibility(visible = isSearchActive) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.search_tasks_hint)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Quick Filter Chips Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilterChip == 0,
                            onClick = { selectedFilterChip = 0 },
                            label = { Text(stringResource(R.string.filter_all)) }
                        )
                        FilterChip(
                            selected = selectedFilterChip == 1,
                            onClick = { selectedFilterChip = 1 },
                            label = { Text(stringResource(R.string.filter_pending)) }
                        )
                        FilterChip(
                            selected = selectedFilterChip == 2,
                            onClick = { selectedFilterChip = 2 },
                            label = { Text(stringResource(R.string.filter_high_priority)) }
                        )
                        FilterChip(
                            selected = selectedFilterChip == 3,
                            onClick = { selectedFilterChip = 3 },
                            label = { Text(stringResource(R.string.filter_has_due_date)) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentView == VikunjaView.MY_TASKS || currentView == VikunjaView.ALL || currentView == VikunjaView.OPEN,
                        onClick = {
                            currentView = VikunjaView.MY_TASKS
                            selectedProjectId = null
                        },
                        icon = { Icon(Icons.Default.CheckCircle, contentDescription = "Tasks") },
                        label = { Text("Tasks", fontWeight = FontWeight.SemiBold) }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToCalendar,
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Schedule") },
                        label = { Text("Schedule") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToMatrix,
                        icon = { Icon(Icons.Default.GridView, contentDescription = "Matrix") },
                        label = { Text("Matrix") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToHabits,
                        icon = { Icon(Icons.Default.LocalFireDepartment, contentDescription = "Habits") },
                        label = { Text("Habits") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToFocus,
                        icon = {
                            BadgedBox(badge = {
                                if (timerState.isRunning) {
                                    Badge(containerColor = Color(0xFF10B981)) { Text("ON") }
                                }
                            }) {
                                Icon(Icons.Default.Timer, contentDescription = "Focus")
                            }
                        },
                        label = { Text("Focus") }
                    )
                }
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
                val currentBaseTasks = remember(
                    currentView,
                    myTasksList,
                    allTasks,
                    todayTasks,
                    completedTasksFiltered,
                    selectedProjectId,
                    overdueTasks,
                    starredTasks
                ) {
                    when (currentView) {
                        VikunjaView.MY_TASKS -> myTasksList
                        VikunjaView.ALL -> allTasks
                        VikunjaView.OPEN -> allTasks.filter { !it.done }
                        VikunjaView.TODAY -> todayTasks
                        VikunjaView.SCHEDULE -> allTasks.filter { it.dueDate != null }
                        VikunjaView.COMPLETED -> completedTasksFiltered
                        VikunjaView.PROJECT -> if (selectedProjectId != null) allTasks.filter { it.projectId == selectedProjectId } else allTasks
                        VikunjaView.OVERDUE -> overdueTasks
                        VikunjaView.STARRED -> starredTasks
                    }
                }

                // Apply Search & Filter Chips with memoization to eliminate scroll lag
                val filteredTasks = remember(currentBaseTasks, searchQuery, selectedFilterChip) {
                    currentBaseTasks.filter { task ->
                        val matchesQuery = if (searchQuery.isBlank()) true else {
                            task.title.contains(searchQuery, ignoreCase = true) ||
                                    task.description.contains(searchQuery, ignoreCase = true)
                        }
                        val matchesChip = when (selectedFilterChip) {
                            1 -> !task.done
                            2 -> task.priority >= 3
                            3 -> task.dueDate != null
                            else -> true
                        }
                        matchesQuery && matchesChip
                    }
                }

                val sortedTasks = remember(filteredTasks, sortOrder) {
                    when (sortOrder) {
                        TaskSortOrder.DEFAULT -> filteredTasks
                        TaskSortOrder.DUE_DATE_ASC -> filteredTasks.sortedWith(
                            compareBy<TaskEntity> { it.dueDate == null }
                                .thenBy { it.dueDate?.let { d -> DateUtils.parseIsoToMillis(d) } ?: Long.MAX_VALUE }
                        )
                        TaskSortOrder.DUE_DATE_DESC -> filteredTasks.sortedWith(
                            compareBy<TaskEntity> { it.dueDate == null }
                                .thenByDescending { it.dueDate?.let { d -> DateUtils.parseIsoToMillis(d) } ?: Long.MIN_VALUE }
                        )
                        TaskSortOrder.PRIORITY_DESC -> filteredTasks.sortedByDescending { it.priority }
                        TaskSortOrder.PRIORITY_ASC -> filteredTasks.sortedBy { it.priority }
                        TaskSortOrder.TITLE_ASC -> filteredTasks.sortedBy { it.title.lowercase() }
                        TaskSortOrder.TITLE_DESC -> filteredTasks.sortedByDescending { it.title.lowercase() }
                        TaskSortOrder.CREATED_DESC -> filteredTasks.sortedWith(
                            compareByDescending { it.created?.let { d -> DateUtils.parseIsoToMillis(d) } ?: 0L }
                        )
                        TaskSortOrder.COMPLETED_DESC -> filteredTasks.sortedWith(
                            compareByDescending { (it.doneAt?.takeIf { d -> d.isNotBlank() } ?: it.updated)?.let { d -> DateUtils.parseIsoToMillis(d) } ?: 0L }
                        )
                    }
                }

                val emptyTextRes = when (currentView) {
                    VikunjaView.MY_TASKS -> R.string.no_tasks_all
                    VikunjaView.ALL -> R.string.no_tasks_all
                    VikunjaView.OPEN -> R.string.no_tasks_open
                    VikunjaView.TODAY -> R.string.no_tasks_today
                    VikunjaView.SCHEDULE -> R.string.no_tasks_schedule
                    VikunjaView.COMPLETED -> R.string.no_tasks_all
                    VikunjaView.PROJECT -> R.string.no_tasks_all
                    VikunjaView.OVERDUE -> R.string.no_tasks_overdue
                    VikunjaView.STARRED -> R.string.no_tasks_starred
                }

                VikunjaTaskList(
                    tasks = sortedTasks,
                    projects = projects,
                    emptyText = stringResource(emptyTextRes),
                    onTaskClick = onNavigateToTask,
                    onToggleDone = { task ->
                        scope.launch { taskRepository.toggleTaskDone(task.id, task.done) }
                    },
                    onToggleFavorite = { task ->
                        scope.launch { taskRepository.toggleTaskFavorite(task.id, task.isFavorite) }
                    },
                    onReschedule = { task ->
                        rescheduleTargetTask = task
                    },
                    onOpenQuickAdd = onOpenQuickAdd
                )

                // Mini Floating Pomodoro Focus Bar (TickTick Feature)
                if (timerState.isRunning) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 76.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth()
                            .clickable { onNavigateToFocus() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎯", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = timerState.taskTitle.ifBlank { "Deep Work Focus" },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1
                                    )
                                    val m = timerState.secondsRemaining / 60
                                    val s = timerState.secondsRemaining % 60
                                    Text(
                                        text = String.format("%02d:%02d remaining", m, s),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (timerState.isPaused) FocusTimerService.resume(context)
                                    else FocusTimerService.pause(context)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (timerState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
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
fun VikunjaTaskList(
    tasks: List<TaskEntity>,
    projects: List<ProjectEntity>,
    emptyText: String,
    onTaskClick: (Long) -> Unit,
    onToggleDone: (TaskEntity) -> Unit,
    onToggleFavorite: (TaskEntity) -> Unit,
    onReschedule: (TaskEntity) -> Unit,
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
                    text = emptyText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.no_tasks_today_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
        val projectMap = remember(projects) { projects.associateBy { it.id } }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Active Tasks with Swipe-to-Action
            items(activeTasks, key = { it.id }) { task ->
                val project = projectMap[task.projectId]
                VikunjaTaskCard(
                    task = task,
                    project = project,
                    onClick = { onTaskClick(task.id) },
                    onToggleDone = { onToggleDone(task) },
                    onToggleFavorite = { onToggleFavorite(task) },
                    onReschedule = { onReschedule(task) }
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
                        val project = projectMap[task.projectId]
                        VikunjaTaskCard(
                            task = task,
                            project = project,
                            onClick = { onTaskClick(task.id) },
                            onToggleDone = { onToggleDone(task) },
                            onToggleFavorite = { onToggleFavorite(task) },
                            onReschedule = { onReschedule(task) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VikunjaTaskCard(
    task: TaskEntity,
    project: ProjectEntity?,
    onClick: () -> Unit,
    onToggleDone: () -> Unit,
    onToggleFavorite: () -> Unit,
    onReschedule: () -> Unit
) {
    val priorityColor = getPriorityColor(task.priority)
    val projectColor = parseProjectColor(project?.hexColor)
    val haptic = LocalHapticFeedback.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleDone()
                false
            } else if (value == SwipeToDismissBoxValue.EndToStart) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onReschedule()
                false
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val bgColor = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Color(0xFF10B981) // Green for Complete
                SwipeToDismissBoxValue.EndToStart -> Color(0xFF3B82F6) // Blue for Reschedule
                else -> Color.Transparent
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Check
                SwipeToDismissBoxValue.EndToStart -> Icons.Default.CalendarToday
                else -> Icons.Default.Check
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (task.done) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (task.done) 0.dp else 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Priority Accent Stripe on the Left
                if (task.priority > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(4.dp)
                            .background(priorityColor)
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Interactive Rounded Checkbox
                    Checkbox(
                        checked = task.done,
                        onCheckedChange = { onToggleDone() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = VikunjaBlue,
                            uncheckedColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Task Details
                    Column(modifier = Modifier.weight(1f)) {
                        // Task Title
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (task.done) FontWeight.Normal else FontWeight.Medium,
                            textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (task.done) {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Description Snippet (cleaned from raw HTML tags)
                        if (task.description.isNotBlank() && !task.done) {
                            val cleanSnippet = remember(task.description) { DescriptionUtils.cleanSnippet(task.description) }
                            if (cleanSnippet.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = cleanSnippet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Metadata Chips Row (Due Date, Project, Priority)
                        val hasDueDate = task.dueDate != null
                        val hasProject = project != null
                        val hasProgress = task.percentDone > 0.0

                        if (hasDueDate || hasProject || hasProgress) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Due Date Badge
                                if (task.dueDate != null) {
                                    val dueMillis = DateUtils.parseIsoToMillis(task.dueDate) ?: 0L
                                    val isOverdue = DateUtils.isOverdue(dueMillis) && !task.done
                                    val isToday = DateUtils.isToday(dueMillis)

                                    val badgeBg = when {
                                        isOverdue -> MaterialTheme.colorScheme.errorContainer
                                        isToday -> Color(0xFFFEF3C7) // Amber tint
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                    val badgeFg = when {
                                        isOverdue -> MaterialTheme.colorScheme.onErrorContainer
                                        isToday -> Color(0xFF92400E)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = badgeBg
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (isOverdue) Icons.Default.Warning else Icons.Default.Schedule,
                                                contentDescription = null,
                                                tint = badgeFg,
                                                modifier = Modifier.size(11.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = DateUtils.formatDisplayDate(task.dueDate),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = badgeFg
                                            )
                                        }
                                    }
                                }

                                // Project Badge
                                if (project != null) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(projectColor)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = project.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                // Progress Indicator
                                if (hasProgress && !task.done) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "${task.percentDone.toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Star / Favorite Button
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (task.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (task.isFavorite) Color(0xFFEAB308) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
