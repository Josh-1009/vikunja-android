package cloud.codella.vikunja.domain.usecase

import cloud.codella.vikunja.domain.model.Task
import cloud.codella.vikunja.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MyDayUseCaseTest {

    private class FakeTaskRepository : TaskRepository {
        val tasks = mutableListOf<Task>()
        var lastResetDate: String? = null

        override fun getTasksFlow(projectId: Long?): Flow<List<Task>> = flowOf(tasks)
        override fun getTaskByIdFlow(taskId: Long): Flow<Task?> = flowOf(tasks.find { it.id == taskId })
        override suspend fun getTaskById(taskId: Long): Task? = tasks.find { it.id == taskId }
        override suspend fun createTask(task: Task): Long {
            tasks.add(task)
            return task.id
        }
        override suspend fun updateTask(task: Task) {
            val idx = tasks.indexOfFirst { it.id == task.id }
            if (idx != -1) tasks[idx] = task
        }
        override suspend fun toggleTaskDone(taskId: Long, isDone: Boolean) {
            val idx = tasks.indexOfFirst { it.id == taskId }
            if (idx != -1) tasks[idx] = tasks[idx].copy(done = isDone)
        }
        override suspend fun deleteTask(taskId: Long) {
            tasks.removeAll { it.id == taskId }
        }

        override fun getMyDayTasksFlow(todayDate: String): Flow<List<Task>> {
            return flowOf(tasks.filter { it.isMyDay && it.myDayDate == todayDate })
        }

        override suspend fun toggleMyDay(taskId: Long, isMyDay: Boolean, todayDate: String) {
            val idx = tasks.indexOfFirst { it.id == taskId }
            if (idx != -1) {
                tasks[idx] = tasks[idx].copy(
                    isMyDay = isMyDay,
                    myDayDate = if (isMyDay) todayDate else null
                )
            }
        }

        override suspend fun resetPastMyDay(todayDate: String) {
            lastResetDate = todayDate
            for (i in tasks.indices) {
                val t = tasks[i]
                if (t.myDayDate != null && t.myDayDate < todayDate) {
                    tasks[i] = t.copy(isMyDay = false, myDayDate = null)
                }
            }
        }

        override fun searchTasksFts(query: String): Flow<List<Task>> {
            return flowOf(tasks.filter { it.title.contains(query, ignoreCase = true) })
        }
    }

    private lateinit var fakeRepo: FakeTaskRepository
    private lateinit var getMyDayTasksUseCase: GetMyDayTasksUseCase
    private lateinit var toggleMyDayUseCase: ToggleMyDayUseCase
    private lateinit var resetMyDayUseCase: ResetMyDayUseCase

    @Before
    fun setup() {
        fakeRepo = FakeTaskRepository()
        getMyDayTasksUseCase = GetMyDayTasksUseCase(fakeRepo)
        toggleMyDayUseCase = ToggleMyDayUseCase(fakeRepo)
        resetMyDayUseCase = ResetMyDayUseCase(fakeRepo)
    }

    @Test
    fun toggleMyDay_addsTaskWithTodayDate() = runTest {
        val task = Task(id = 1L, title = "Finish Sprint", projectId = 1L)
        fakeRepo.tasks.add(task)

        toggleMyDayUseCase(taskId = 1L, isMyDay = true, todayDate = "2026-10-16")

        val updated = fakeRepo.tasks.first { it.id == 1L }
        assertTrue(updated.isMyDay)
        assertEquals("2026-10-16", updated.myDayDate)
    }

    @Test
    fun toggleMyDay_removesTaskFromMyDay() = runTest {
        val task = Task(id = 2L, title = "Old Task", projectId = 1L, isMyDay = true, myDayDate = "2026-10-16")
        fakeRepo.tasks.add(task)

        toggleMyDayUseCase(taskId = 2L, isMyDay = false, todayDate = "2026-10-16")

        val updated = fakeRepo.tasks.first { it.id == 2L }
        assertFalse(updated.isMyDay)
        assertNull(updated.myDayDate)
    }

    @Test
    fun resetPastMyDay_clearsExpiredTasksFromYesterday() = runTest {
        val yesterdayTask = Task(id = 3L, title = "Yesterday Task", projectId = 1L, isMyDay = true, myDayDate = "2026-10-15")
        val todayTask = Task(id = 4L, title = "Today Task", projectId = 1L, isMyDay = true, myDayDate = "2026-10-16")
        fakeRepo.tasks.addAll(listOf(yesterdayTask, todayTask))

        resetMyDayUseCase(todayDate = "2026-10-16")

        val updatedYesterday = fakeRepo.tasks.first { it.id == 3L }
        val updatedToday = fakeRepo.tasks.first { it.id == 4L }

        assertFalse(updatedYesterday.isMyDay)
        assertNull(updatedYesterday.myDayDate)

        assertTrue(updatedToday.isMyDay)
        assertEquals("2026-10-16", updatedToday.myDayDate)
    }

    @Test
    fun getMyDayTasksUseCase_autoPrunesPastTasksAndReturnsTodayOnly() = runTest {
        val staleTask = Task(id = 5L, title = "Stale Task", projectId = 1L, isMyDay = true, myDayDate = "2026-10-14")
        val freshTask = Task(id = 6L, title = "Fresh Task", projectId = 1L, isMyDay = true, myDayDate = "2026-10-16")
        fakeRepo.tasks.addAll(listOf(staleTask, freshTask))

        val result = getMyDayTasksUseCase(todayDate = "2026-10-16").first()

        assertEquals("2026-10-16", fakeRepo.lastResetDate)
        assertEquals(1, result.size)
        assertEquals(6L, result.first().id)
    }
}
