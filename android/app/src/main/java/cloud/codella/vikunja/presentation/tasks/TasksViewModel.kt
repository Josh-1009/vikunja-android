package cloud.codella.vikunja.presentation.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cloud.codella.vikunja.domain.model.Project
import cloud.codella.vikunja.domain.model.SyncResult
import cloud.codella.vikunja.domain.model.Task
import cloud.codella.vikunja.domain.repository.ProjectRepository
import cloud.codella.vikunja.domain.repository.TaskRepository
import cloud.codella.vikunja.domain.usecase.CreateTaskUseCase
import cloud.codella.vikunja.domain.usecase.GetTasksFlowUseCase
import cloud.codella.vikunja.domain.usecase.ToggleTaskDoneUseCase
import cloud.codella.vikunja.domain.usecase.TriggerSyncUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TasksViewModel(
    private val getTasksFlowUseCase: GetTasksFlowUseCase,
    private val createTaskUseCase: CreateTaskUseCase,
    private val toggleTaskDoneUseCase: ToggleTaskDoneUseCase,
    private val triggerSyncUseCase: TriggerSyncUseCase,
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val taskRelationRepository: cloud.codella.vikunja.domain.repository.TaskRelationRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    private val _effects = Channel<TasksSideEffect>(Channel.BUFFERED)
    val effects: Flow<TasksSideEffect> = _effects.receiveAsFlow()

    // Filter controls
    private val selectedProjectId = MutableStateFlow<Long?>(null)
    private val selectedSmartView = MutableStateFlow(SmartViewType.INBOX)
    private val searchQuery = MutableStateFlow("")

    private var recentlyDeletedTask: Task? = null

    init {
        observeProjects()
        observeTasks()
        refreshSync()
    }

    private fun observeProjects() {
        viewModelScope.launch {
            projectRepository.getProjectsFlow().collect { projects ->
                _uiState.update { it.copy(allProjects = projects) }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    private fun observeTasks() {
        viewModelScope.launch {
            val blockedIdsFlow = taskRelationRepository?.getBlockedTaskIdsFlow() ?: flowOf(emptySet())
            searchQuery
                .debounce(200L)
                .flatMapLatest { query ->
                    val sourceTasksFlow = if (query.isNotBlank()) {
                        taskRepository.searchTasksFts(query)
                    } else {
                        getTasksFlowUseCase()
                    }
                    combine(
                        sourceTasksFlow,
                        blockedIdsFlow,
                        selectedProjectId,
                        selectedSmartView
                    ) { tasks, blockedIds, projectId, smartView ->
                        val annotatedTasks = tasks.map { task ->
                            if (task.id in blockedIds) task.copy(isBlocked = true) else task
                        }
                        filterAndCategorize(annotatedTasks, projectId, smartView, query)
                    }
                }.collect { categorized ->
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            overdueTasks = categorized.overdue,
                            todayTasks = categorized.today,
                            upcomingTasks = categorized.upcoming,
                            completedTasks = categorized.completed
                        )
                    }
                }
        }
    }

    private data class CategorizedTasks(
        val overdue: List<Task>,
        val today: List<Task>,
        val upcoming: List<Task>,
        val completed: List<Task>
    )

    private fun filterAndCategorize(
        tasks: List<Task>,
        projectId: Long?,
        smartView: SmartViewType,
        query: String
    ): CategorizedTasks {
        val today = LocalDate.now()
        val isFtsActive = query.isNotBlank()

        val filtered = tasks.filter { task ->
            val matchesProject = projectId == null || task.projectId == projectId
            val matchesQuery = isFtsActive || query.isBlank() || task.title.contains(query, ignoreCase = true) ||
                task.description.contains(query, ignoreCase = true)
            matchesProject && matchesQuery
        }

        val overdue = mutableListOf<Task>()
        val todayList = mutableListOf<Task>()
        val upcoming = mutableListOf<Task>()
        val completed = mutableListOf<Task>()

        for (task in filtered) {
            if (task.done) {
                completed.add(task)
                continue
            }

            val dueLocalDate = parseDateToLocal(task.dueDate)
            if (dueLocalDate == null) {
                // Tasks with no due date fall into Today if in Inbox, or Upcoming
                todayList.add(task)
            } else {
                when {
                    dueLocalDate.isBefore(today) -> overdue.add(task)
                    dueLocalDate.isEqual(today) -> todayList.add(task)
                    else -> upcoming.add(task)
                }
            }
        }

        // Apply Smart View filters
        return when (smartView) {
            SmartViewType.INBOX -> CategorizedTasks(overdue, todayList, upcoming, completed)
            SmartViewType.TODAY -> CategorizedTasks(overdue, todayList, emptyList(), emptyList())
            SmartViewType.UPCOMING -> CategorizedTasks(emptyList(), emptyList(), upcoming, emptyList())
            SmartViewType.COMPLETED -> CategorizedTasks(emptyList(), emptyList(), emptyList(), completed)
        }
    }

    private fun parseDateToLocal(isoString: String?): LocalDate? {
        if (isoString.isNullOrBlank()) return null
        return try {
            Instant.parse(isoString).atZone(ZoneId.systemDefault()).toLocalDate()
        } catch (_: Exception) {
            try {
                LocalDate.parse(isoString.substringBefore("T"), DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun onEvent(event: TasksUiEvent) {
        when (event) {
            is TasksUiEvent.ToggleTaskDone -> {
                viewModelScope.launch {
                    _effects.send(TasksSideEffect.TriggerHaptic)
                    toggleTaskDoneUseCase(event.taskId, event.isDone)
                }
            }
            is TasksUiEvent.DeleteTask -> {
                viewModelScope.launch {
                    recentlyDeletedTask = event.task
                    taskRepository.deleteTask(event.task.id)
                    _effects.send(TasksSideEffect.TriggerHaptic)
                    _effects.send(
                        TasksSideEffect.ShowSnackbar(
                            message = "Task \"${event.task.title}\" deleted",
                            actionLabel = "Undo",
                            onAction = { onEvent(TasksUiEvent.UndoDelete(event.task)) }
                        )
                    )
                }
            }
            is TasksUiEvent.UndoDelete -> {
                viewModelScope.launch {
                    val restored = event.task.copy(id = 0L)
                    createTaskUseCase(restored)
                    recentlyDeletedTask = null
                }
            }
            is TasksUiEvent.CreateTaskFromDraft -> {
                viewModelScope.launch {
                    val targetProject = event.targetProjectId
                        ?: _uiState.value.selectedProjectId
                        ?: _uiState.value.allProjects.firstOrNull()?.id
                        ?: 1L

                    val newTask = Task(
                        id = 0L,
                        title = event.cleanTitle,
                        projectId = targetProject,
                        dueDate = event.dueDateIso,
                        priority = event.priority
                    )
                    createTaskUseCase(newTask)
                    _effects.send(TasksSideEffect.TriggerHaptic)
                }
            }
            is TasksUiEvent.SelectProject -> {
                selectedProjectId.value = event.projectId
                _uiState.update {
                    it.copy(
                        selectedProjectId = event.projectId,
                        selectedProjectTitle = event.projectTitle
                    )
                }
            }
            is TasksUiEvent.SelectSmartView -> {
                selectedSmartView.value = event.view
                selectedProjectId.value = null
                val title = when (event.view) {
                    SmartViewType.INBOX -> "Inbox"
                    SmartViewType.TODAY -> "Today"
                    SmartViewType.UPCOMING -> "Upcoming"
                    SmartViewType.COMPLETED -> "Completed"
                }
                _uiState.update {
                    it.copy(
                        selectedSmartView = event.view,
                        selectedProjectId = null,
                        selectedProjectTitle = title
                    )
                }
            }
            is TasksUiEvent.UpdateSearchQuery -> {
                searchQuery.value = event.query
                _uiState.update { it.copy(searchQuery = event.query) }
            }
            is TasksUiEvent.ToggleSearch -> {
                _uiState.update { it.copy(isSearchActive = event.active) }
                if (!event.active) {
                    searchQuery.value = ""
                    _uiState.update { it.copy(searchQuery = "") }
                }
            }
            is TasksUiEvent.SwitchViewMode -> {
                _uiState.update { it.copy(viewMode = event.mode) }
            }
            is TasksUiEvent.RefreshSync -> {
                refreshSync()
            }
        }
    }

    private fun refreshSync() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncErrorMessage = null) }
            when (val result = triggerSyncUseCase()) {
                is SyncResult.Success -> {
                    _uiState.update { it.copy(isSyncing = false) }
                }
                is SyncResult.Partial -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            syncErrorMessage = result.errors.firstOrNull()
                        )
                    }
                }
                is SyncResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            syncErrorMessage = result.cause.localizedMessage ?: "Sync error"
                        )
                    }
                }
            }
        }
    }
}
