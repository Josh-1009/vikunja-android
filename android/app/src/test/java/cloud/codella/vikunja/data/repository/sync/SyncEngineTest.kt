package cloud.codella.vikunja.data.repository.sync

import cloud.codella.vikunja.data.local.dao.ProjectDao
import cloud.codella.vikunja.data.local.dao.SyncQueueDao
import cloud.codella.vikunja.data.local.dao.TaskDao
import cloud.codella.vikunja.data.local.entity.SyncQueueEntity
import cloud.codella.vikunja.data.remote.api.VikunjaRemoteApi
import cloud.codella.vikunja.data.remote.dto.RemoteTaskDto
import cloud.codella.vikunja.domain.model.SyncResult
import io.mockk.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class SyncEngineTest {

    private val syncQueueDao = mockk<SyncQueueDao>(relaxed = true)
    private val taskDao = mockk<TaskDao>(relaxed = true)
    private val projectDao = mockk<ProjectDao>(relaxed = true)
    private val api = mockk<VikunjaRemoteApi>()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var syncEngine: SyncEngine

    @Before
    fun setUp() {
        clearAllMocks()
        syncEngine = SyncEngine(
            syncQueueDao = syncQueueDao,
            taskDao = taskDao,
            projectDao = projectDao,
            api = api,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `successful sync pushes create task, updates negative ID to server ID atomically, and dequeues item`() = runTest(testDispatcher) {
        val negativeId = -123456L
        val realServerId = 888L
        val syncQueueId = 1L

        val pendingItem = SyncQueueEntity(
            id = syncQueueId,
            entityType = "TASK",
            entityId = negativeId,
            operation = "CREATE",
            payloadJson = """{"title":"Draft Architecture","project_id":5}"""
        )

        val serverResponse = RemoteTaskDto(
            id = realServerId,
            title = "Draft Architecture",
            projectId = 5L,
            updated = "2026-10-16T12:00:00Z"
        )

        coEvery { syncQueueDao.getPendingSyncItems() } returns listOf(pendingItem)
        coEvery { api.createTask(5L, any()) } returns serverResponse
        coEvery { taskDao.reconcileTaskId(any(), any(), any()) } just Runs
        coEvery { syncQueueDao.updateEntityIdInQueue(any(), any(), any()) } just Runs
        coEvery { syncQueueDao.dequeue(any()) } just Runs
        coEvery { syncQueueDao.getPendingTaskIds() } returns emptyList()
        coEvery { api.getAllTasks(any(), any(), any(), any()) } returns emptyList()

        val result = syncEngine.sync()

        // 1. Must reconcile negative ID to real server ID in Room atomically
        coVerify(exactly = 1) {
            taskDao.reconcileTaskId(
                oldId = negativeId,
                newId = realServerId,
                updatedServerTask = match { it.id == realServerId && it.title == "Draft Architecture" }
            )
        }

        // 2. Must update any queued downstream operations that used the negative ID
        coVerify(exactly = 1) {
            syncQueueDao.updateEntityIdInQueue("TASK", negativeId, realServerId)
        }

        // 3. Must dequeue the completed mutation
        coVerify(exactly = 1) {
            syncQueueDao.dequeue(syncQueueId)
        }

        assertTrue("Result should be Success", result is SyncResult.Success)
        assertEquals(1, (result as SyncResult.Success).pushedCount)
    }

    @Test
    fun `successful sync pushes update task, reconciles and dequeues item`() = runTest(testDispatcher) {
        val serverTaskId = 42L
        val syncQueueId = 2L

        val pendingItem = SyncQueueEntity(
            id = syncQueueId,
            entityType = "TASK",
            entityId = serverTaskId,
            operation = "UPDATE",
            payloadJson = """{"done":true}"""
        )

        val serverResponse = RemoteTaskDto(
            id = serverTaskId,
            title = "Existing Task",
            done = true,
            projectId = 1L,
            updated = "2026-10-16T12:05:00Z"
        )

        coEvery { syncQueueDao.getPendingSyncItems() } returns listOf(pendingItem)
        coEvery { api.updateTask(serverTaskId, any()) } returns serverResponse
        coEvery { taskDao.insertTask(any()) } returns 1L
        coEvery { syncQueueDao.dequeue(syncQueueId) } just Runs
        coEvery { syncQueueDao.getPendingTaskIds() } returns emptyList()
        coEvery { api.getAllTasks(any(), any(), any(), any()) } returns emptyList()

        val result = syncEngine.sync()

        coVerify(exactly = 1) {
            taskDao.insertTask(match { it.id == serverTaskId && it.done })
        }
        coVerify(exactly = 1) {
            syncQueueDao.dequeue(syncQueueId)
        }
        assertTrue(result is SyncResult.Success)
        assertEquals(1, (result as SyncResult.Success).pushedCount)
    }

    @Test
    fun `network failure during sync retains item in queue and increments retry count`() = runTest(testDispatcher) {
        val negativeId = -999L
        val syncQueueId = 3L

        val pendingItem = SyncQueueEntity(
            id = syncQueueId,
            entityType = "TASK",
            entityId = negativeId,
            operation = "CREATE",
            payloadJson = """{"title":"Failing Task","project_id":1}""",
            retryCount = 0
        )

        coEvery { syncQueueDao.getPendingSyncItems() } returns listOf(pendingItem)
        coEvery { api.createTask(any(), any()) } throws IOException("503 Server Unavailable")
        coEvery { syncQueueDao.incrementRetryCount(syncQueueId) } just Runs
        coEvery { syncQueueDao.getPendingTaskIds() } returns listOf(negativeId)
        coEvery { api.getAllTasks(any(), any(), any(), any()) } returns emptyList()

        val result = syncEngine.sync()

        // Must increment retry count on failure
        coVerify(exactly = 1) {
            syncQueueDao.incrementRetryCount(syncQueueId)
        }

        // Must NOT dequeue the failing item
        coVerify(exactly = 0) {
            syncQueueDao.dequeue(syncQueueId)
        }

        // Must NOT attempt to reconcile ID
        coVerify(exactly = 0) {
            taskDao.reconcileTaskId(any(), any(), any())
        }

        assertTrue("Result should record errors/partial sync", result is SyncResult.Partial)
    }
}
