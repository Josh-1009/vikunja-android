package cloud.codella.vikunja.data.repository

import cloud.codella.vikunja.data.local.dao.SyncQueueDao
import cloud.codella.vikunja.data.local.dao.TaskDao
import cloud.codella.vikunja.data.local.entity.SyncQueueEntity
import cloud.codella.vikunja.data.mapper.toDomain
import cloud.codella.vikunja.data.mapper.toEntity
import cloud.codella.vikunja.data.mapper.toMutationRequest
import cloud.codella.vikunja.data.remote.dto.RemoteTaskMutationRequest
import cloud.codella.vikunja.domain.model.Task
import cloud.codella.vikunja.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TaskRepositoryImpl(
    private val taskDao: TaskDao,
    private val syncQueueDao: SyncQueueDao,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) : TaskRepository {

    override fun getTasksFlow(projectId: Long?): Flow<List<Task>> {
        val flow = if (projectId != null) {
            taskDao.getTasksByProjectFlow(projectId)
        } else {
            taskDao.getAllTasksFlow()
        }
        return flow.map { entities -> entities.map { it.toDomain() } }
    }

    override fun getTaskByIdFlow(taskId: Long): Flow<Task?> {
        return taskDao.getTaskByIdFlow(taskId).map { it?.toDomain() }
    }

    override suspend fun getTaskById(taskId: Long): Task? {
        return taskDao.getTaskById(taskId)?.toDomain()
    }

    override suspend fun createTask(task: Task): Long {
        val localNegativeId = -System.currentTimeMillis()
        val nowIso = java.time.Instant.now().toString()
        val localTask = task.copy(
            id = localNegativeId,
            created = nowIso,
            updated = nowIso
        )
        taskDao.insertTask(localTask.toEntity())

        val payload = json.encodeToString(localTask.toMutationRequest())
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "TASK",
                entityId = localNegativeId,
                operation = "CREATE",
                payloadJson = payload,
                createdAt = System.currentTimeMillis()
            )
        )
        return localNegativeId
    }

    override suspend fun updateTask(task: Task) {
        val nowIso = java.time.Instant.now().toString()
        val updatedTask = task.copy(updated = nowIso)
        taskDao.updateTask(updatedTask.toEntity())

        val payload = json.encodeToString(updatedTask.toMutationRequest())
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "TASK",
                entityId = task.id,
                operation = "UPDATE",
                payloadJson = payload,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun toggleTaskDone(taskId: Long, isDone: Boolean) {
        val nowIso = java.time.Instant.now().toString()
        val doneAt = if (isDone) nowIso else null
        taskDao.updateTaskDone(taskId, isDone, doneAt, nowIso)

        val mutation = RemoteTaskMutationRequest(done = isDone)
        val payload = json.encodeToString(mutation)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "TASK",
                entityId = taskId,
                operation = "UPDATE",
                payloadJson = payload,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteTask(taskId: Long) {
        taskDao.deleteTask(taskId)
        if (taskId < 0) {
            syncQueueDao.deleteByEntity("TASK", taskId)
        } else {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    entityType = "TASK",
                    entityId = taskId,
                    operation = "DELETE",
                    payloadJson = "{}",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    override fun getMyDayTasksFlow(todayDate: String): Flow<List<Task>> {
        return taskDao.getMyDayTasksFlow(todayDate).map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun toggleMyDay(taskId: Long, isMyDay: Boolean, todayDate: String) {
        val date = if (isMyDay) todayDate else null
        taskDao.updateTaskMyDay(taskId, isMyDay, date)
    }

    override suspend fun resetPastMyDay(todayDate: String) {
        taskDao.resetPastMyDayTasks(todayDate)
    }

    override fun searchTasksFts(query: String): Flow<List<Task>> {
        return taskDao.searchTasksWithPrefix(query).map { entities -> entities.map { it.toDomain() } }
    }
}
