package cloud.codella.vikunja.domain.usecase

import cloud.codella.vikunja.domain.repository.TaskRepository
import java.time.LocalDate

class ResetMyDayUseCase(
    private val taskRepository: TaskRepository
) {
    suspend operator fun invoke(todayDate: String = LocalDate.now().toString()) {
        taskRepository.resetPastMyDay(todayDate)
    }
}
