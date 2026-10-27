package cloud.codella.vikunja.domain.usecase

import cloud.codella.vikunja.domain.model.Task
import cloud.codella.vikunja.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow

class GetTasksFlowUseCase(
    private val taskRepository: TaskRepository
) {
    operator fun invoke(projectId: Long? = null): Flow<List<Task>> =
        taskRepository.getTasksFlow(projectId)
}
