package cloud.codella.vikunja.data.repository

import cloud.codella.vikunja.data.local.dao.SyncQueueDao
import cloud.codella.vikunja.data.local.dao.TaskDao
import cloud.codella.vikunja.data.local.entity.TaskEntity
import cloud.codella.vikunja.domain.model.Task
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TaskRepositoryTest {

    private val taskDao = mockk<TaskDao>(relaxed = true)
    private val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
    private lateinit var repository: TaskRepositoryImpl

    @Before
    fun setUp() {
        clearAllMocks()
        repository = TaskRepositoryImpl(
            taskDao = taskDao,
            syncQueueDao = syncQueueDao
        )
    }

    @Test
    fun `offline task creation writes optimistic entity with negative ID and enqueues sync record`() = runTest {
        val domainTask = Task(
            id = 0L,
            title = "Implement Offline Sync",
            description = "Clean Architecture Phase 1",
            projectId = 10L,
            priority = 3
        )

        coEvery { taskDao.insertTask(any()) } returns 1L
        coEvery { syncQueueDao.enqueue(any()) } returns 1L

        val returnedId = repository.createTask(domainTask)

        // ID must be negative for optimistic local tasks
        assertTrue("Local task ID must be negative", returnedId < 0)

        // Room must receive the task with the assigned negative ID
        coVerify(exactly = 1) {
            taskDao.insertTask(match {
                it.id == returnedId &&
                    it.title == "Implement Offline Sync" &&
                    it.projectId == 10L
            })
        }

        // Sync queue must record a CREATE operation for this negative ID
        coVerify(exactly = 1) {
            syncQueueDao.enqueue(match {
                it.entityType == "TASK" &&
                    it.entityId == returnedId &&
                    it.operation == "CREATE" &&
                    it.payloadJson.contains("Implement Offline Sync")
            })
        }
    }

    @Test
    fun `toggle task done updates room immediately and enqueues update mutation`() = runTest {
        val taskId = 42L
        coEvery { taskDao.updateTaskDone(any(), any(), any(), any()) } just Runs
        coEvery { syncQueueDao.enqueue(any()) } returns 1L

        repository.toggleTaskDone(taskId, isDone = true)

        coVerify(exactly = 1) {
            taskDao.updateTaskDone(
                id = eq(taskId),
                done = eq(true),
                doneAt = match { it != null && it.isNotBlank() },
                updated = match { it.isNotBlank() }
            )
        }

        coVerify(exactly = 1) {
            syncQueueDao.enqueue(match {
                it.entityType == "TASK" &&
                    it.entityId == taskId &&
                    it.operation == "UPDATE" &&
                    it.payloadJson.contains("\"done\":true")
            })
        }
    }

    @Test
    fun `deleting an un-synced negative ID task cancels pending sync and removes local entity`() = runTest {
        val negativeId = -9999L
        coEvery { taskDao.deleteTask(any()) } just Runs
        coEvery { syncQueueDao.deleteByEntity(any(), any()) } just Runs

        repository.deleteTask(negativeId)

        coVerify(exactly = 1) { taskDao.deleteTask(negativeId) }
        coVerify(exactly = 1) { syncQueueDao.deleteByEntity("TASK", negativeId) }
        coVerify(exactly = 0) { syncQueueDao.enqueue(any()) }
    }

    @Test
    fun `deleting a synced positive ID task enqueues DELETE mutation`() = runTest {
        val serverId = 100L
        coEvery { taskDao.deleteTask(any()) } just Runs
        coEvery { syncQueueDao.enqueue(any()) } returns 1L

        repository.deleteTask(serverId)

        coVerify(exactly = 1) { taskDao.deleteTask(serverId) }
        coVerify(exactly = 1) {
            syncQueueDao.enqueue(match {
                it.entityType == "TASK" &&
                    it.entityId == serverId &&
                    it.operation == "DELETE"
            })
        }
    }
}
