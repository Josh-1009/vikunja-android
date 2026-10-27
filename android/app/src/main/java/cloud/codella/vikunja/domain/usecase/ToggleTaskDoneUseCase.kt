package cloud.codella.vikunja.domain.usecase

import cloud.codella.vikunja.domain.repository.TaskRepository

class ToggleTaskDoneUseCase(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(taskId: Long, isDone: Boolean) {
        taskRepository.toggleTaskDone(taskId, isDone)
    }
}
