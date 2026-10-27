package cloud.codella.vikunja.presentation.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cloud.codella.vikunja.domain.repository.ProjectRepository
import cloud.codella.vikunja.domain.repository.TaskRepository
import cloud.codella.vikunja.domain.usecase.CreateTaskUseCase
import cloud.codella.vikunja.domain.usecase.GetTasksFlowUseCase
import cloud.codella.vikunja.domain.usecase.ToggleTaskDoneUseCase
import cloud.codella.vikunja.domain.usecase.TriggerSyncUseCase

class TasksViewModelFactory(
    private val getTasksFlowUseCase: GetTasksFlowUseCase,
    private val createTaskUseCase: CreateTaskUseCase,
    private val toggleTaskDoneUseCase: ToggleTaskDoneUseCase,
    private val triggerSyncUseCase: TriggerSyncUseCase,
    private val taskRepository: TaskRepository,
    private val projectRepository: ProjectRepository,
    private val taskRelationRepository: cloud.codella.vikunja.domain.repository.TaskRelationRepository? = null
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TasksViewModel::class.java)) {
            return TasksViewModel(
                getTasksFlowUseCase = getTasksFlowUseCase,
                createTaskUseCase = createTaskUseCase,
                toggleTaskDoneUseCase = toggleTaskDoneUseCase,
                triggerSyncUseCase = triggerSyncUseCase,
                taskRepository = taskRepository,
                projectRepository = projectRepository,
                taskRelationRepository = taskRelationRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
