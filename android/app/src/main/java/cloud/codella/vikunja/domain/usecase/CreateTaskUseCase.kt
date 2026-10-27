package cloud.codella.vikunja.domain.usecase

import cloud.codella.vikunja.domain.model.Task
import cloud.codella.vikunja.domain.repository.TaskRepository

class CreateTaskUseCase(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(task: Task): Long {
        require(task.title.isNotBlank()) { "Task title cannot be blank" }
        return taskRepository.createTask(task)
    }
}
