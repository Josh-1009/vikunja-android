package cloud.codella.vikunja.domain.usecase

import cloud.codella.vikunja.domain.model.Task
import cloud.codella.vikunja.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

class GetMyDayTasksUseCase(
    private val taskRepository: TaskRepository
) {
    operator fun invoke(todayDate: String = LocalDate.now().toString()): Flow<List<Task>> = flow {
        // Automatically prune/reset any stale My Day tasks left over from previous days
        taskRepository.resetPastMyDay(todayDate)
        emitAll(taskRepository.getMyDayTasksFlow(todayDate))
    }
}
