package cloud.codella.vikunja.domain.repository

import cloud.codella.vikunja.domain.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun getTasksFlow(projectId: Long? = null): Flow<List<Task>>
    fun getTaskByIdFlow(taskId: Long): Flow<Task?>
    suspend fun getTaskById(taskId: Long): Task?
    suspend fun createTask(task: Task): Long
    suspend fun updateTask(task: Task)
    suspend fun toggleTaskDone(taskId: Long, isDone: Boolean)
    suspend fun deleteTask(taskId: Long)
    fun getMyDayTasksFlow(todayDate: String): Flow<List<Task>>
    suspend fun toggleMyDay(taskId: Long, isMyDay: Boolean, todayDate: String)
    suspend fun resetPastMyDay(todayDate: String)
    fun searchTasksFts(query: String): Flow<List<Task>>
}
