package cloud.codella.vikunja.domain.usecase

import cloud.codella.vikunja.domain.repository.TaskRepository
import java.time.LocalDate

class ToggleMyDayUseCase(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(taskId: Long, isMyDay: Boolean, todayDate: String = LocalDate.now().toString()) {
        taskRepository.toggleMyDay(taskId, isMyDay, todayDate)
    }
}
