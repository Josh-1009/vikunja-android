package cloud.codella.vikunja.presentation.tasks

import androidx.compose.runtime.Immutable
import cloud.codella.vikunja.domain.model.Project
import cloud.codella.vikunja.domain.model.Task

enum class ViewMode {
    LIST,
    KANBAN
}

enum class SmartViewType {
    INBOX,
    TODAY,
    UPCOMING,
    COMPLETED
}

@Immutable
data class TasksUiState(
    val isLoading: Boolean = true,
    val overdueTasks: List<Task> = emptyList(),
    val todayTasks: List<Task> = emptyList(),
    val upcomingTasks: List<Task> = emptyList(),
    val completedTasks: List<Task> = emptyList(),
    val allProjects: List<Project> = emptyList(),
    val selectedSmartView: SmartViewType = SmartViewType.INBOX,
    val selectedProjectId: Long? = null,
    val selectedProjectTitle: String = "All Tasks",
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isSyncing: Boolean = false,
    val syncErrorMessage: String? = null,
    val viewMode: ViewMode = ViewMode.LIST
) {
    val totalTaskCount: Int
        get() = overdueTasks.size + todayTasks.size + upcomingTasks.size + completedTasks.size

    val isEmpty: Boolean
        get() = !isLoading && totalTaskCount == 0
}

sealed interface TasksUiEvent {
    data class ToggleTaskDone(val taskId: Long, val isDone: Boolean) : TasksUiEvent
    data class DeleteTask(val task: Task) : TasksUiEvent
    data class UndoDelete(val task: Task) : TasksUiEvent
    data class CreateTaskFromDraft(
        val cleanTitle: String,
        val dueDateIso: String? = null,
        val priority: Int = 0,
        val labels: List<String> = emptyList(),
        val targetProjectId: Long? = null
    ) : TasksUiEvent
    data class SelectProject(val projectId: Long?, val projectTitle: String) : TasksUiEvent
    data class SelectSmartView(val view: SmartViewType) : TasksUiEvent
    data class UpdateSearchQuery(val query: String) : TasksUiEvent
    data class ToggleSearch(val active: Boolean) : TasksUiEvent
    data class SwitchViewMode(val mode: ViewMode) : TasksUiEvent
    data object RefreshSync : TasksUiEvent
}

sealed interface TasksSideEffect {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null
    ) : TasksSideEffect
    data object TriggerHaptic : TasksSideEffect
    data class NavigateToDetail(val taskId: Long) : TasksSideEffect
}
