package cloud.codella.vikunja.data.repository.sync

import cloud.codella.vikunja.data.local.dao.ProjectDao
import cloud.codella.vikunja.data.local.dao.SyncQueueDao
import cloud.codella.vikunja.data.local.dao.TaskDao
import cloud.codella.vikunja.data.local.entity.SyncQueueEntity
import cloud.codella.vikunja.data.mapper.toEntity
import cloud.codella.vikunja.data.remote.api.VikunjaRemoteApi
import cloud.codella.vikunja.data.remote.dto.RemoteProjectMutationRequest
import cloud.codella.vikunja.data.remote.dto.RemoteTaskMutationRequest
import cloud.codella.vikunja.domain.model.SyncResult
import cloud.codella.vikunja.domain.repository.SyncEngineContract
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class SyncEngine(
    private val syncQueueDao: SyncQueueDao,
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao,
    private val api: VikunjaRemoteApi,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SyncEngineContract {

    private val syncMutex = Mutex()

    override suspend fun sync(): SyncResult = withContext(ioDispatcher) {
        syncMutex.withLock {
            var pushedCount = 0
            var pulledCount = 0
            val errors = mutableListOf<String>()

            // 1. PUSH PHASE: Coalesce mutations & process in FIFO order
            try {
                val pendingItems = syncQueueDao.getPendingSyncItems()
                val coalesced = coalesceMutations(pendingItems)
                for (supersededId in coalesced.supersededItemIds) {
                    syncQueueDao.dequeue(supersededId)
                }

                val remappedIds = mutableMapOf<Pair<String, Long>, Long>()

                for (item in coalesced.itemsToProcess) {
                    val actualEntityId = remappedIds[item.entityType to item.entityId] ?: item.entityId
                    try {
                        when (item.entityType) {
                            "TASK" -> {
                                when (item.operation) {
                                    "CREATE" -> {
                                        val request = json.decodeFromString<RemoteTaskMutationRequest>(item.payloadJson)
                                        val targetProjectId = request.projectId
                                            ?: taskDao.getTaskById(actualEntityId)?.projectId
                                            ?: 1L

                                        val serverTask = api.createTask(targetProjectId, request)
                                        // Atomically replace local negative ID with the real server ID
                                        taskDao.reconcileTaskId(actualEntityId, serverTask.id, serverTask.toEntity())
                                        // Update any downstream queued mutations referring to this negative ID
                                        syncQueueDao.updateEntityIdInQueue("TASK", actualEntityId, serverTask.id)
                                        remappedIds["TASK" to actualEntityId] = serverTask.id
                                        syncQueueDao.dequeue(item.id)
                                        pushedCount++
                                    }
                                    "UPDATE" -> {
                                        val request = json.decodeFromString<RemoteTaskMutationRequest>(item.payloadJson)
                                        val serverTask = api.updateTask(actualEntityId, request)
                                        taskDao.insertTask(serverTask.toEntity())
                                        syncQueueDao.dequeue(item.id)
                                        pushedCount++
                                    }
                                    "DELETE" -> {
                                        api.deleteTask(actualEntityId)
                                        syncQueueDao.dequeue(item.id)
                                        pushedCount++
                                    }
                                }
                            }
                            "PROJECT" -> {
                                when (item.operation) {
                                    "CREATE" -> {
                                        val request = json.decodeFromString<RemoteProjectMutationRequest>(item.payloadJson)
                                        val serverProject = api.createProject(request)
                                        projectDao.reconcileProjectId(actualEntityId, serverProject.id, serverProject.toEntity())
                                        syncQueueDao.updateEntityIdInQueue("PROJECT", actualEntityId, serverProject.id)
                                        remappedIds["PROJECT" to actualEntityId] = serverProject.id
                                        syncQueueDao.dequeue(item.id)
                                        pushedCount++
                                    }
                                    "UPDATE" -> {
                                        val request = json.decodeFromString<RemoteProjectMutationRequest>(item.payloadJson)
                                        val serverProject = api.updateProject(actualEntityId, request)
                                        projectDao.insertProject(serverProject.toEntity())
                                        syncQueueDao.dequeue(item.id)
                                        pushedCount++
                                    }
                                    "DELETE" -> {
                                        api.deleteProject(actualEntityId)
                                        syncQueueDao.dequeue(item.id)
                                        pushedCount++
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Network or server error on mutation: retain in queue and increment retry
                        syncQueueDao.incrementRetryCount(item.id)
                        errors.add("Mutation failed for ${item.entityType} #$actualEntityId: ${e.message}")
                        // Stop processing subsequent mutations for safety and FIFO preservation
                        break
                    }
                }
            } catch (e: Exception) {
                return@withContext SyncResult.Failure(e)
            }

            // 2. PULL PHASE: Reconcile downstream state from server
            try {
                val pendingTaskIds = syncQueueDao.getPendingTaskIds().toSet()
                val remoteTasks = api.getAllTasks(page = 1, perPage = 100)

                for (remote in remoteTasks) {
                    // Prevent overwriting local tasks with pending offline mutations
                    if (remote.id in pendingTaskIds) continue

                    val local = taskDao.getTaskById(remote.id)
                    if (local == null || remote.updated >= local.updated) {
                        taskDao.insertTask(remote.toEntity())
                        pulledCount++
                    }
                }
            } catch (e: Exception) {
                errors.add("Pull failed: ${e.message}")
            }

            if (errors.isEmpty()) {
                SyncResult.Success(pushedCount, pulledCount)
            } else {
                SyncResult.Partial(pushedCount, pulledCount, errors)
            }
        }
    }

    data class CoalescedMutations(
        val itemsToProcess: List<SyncQueueEntity>,
        val supersededItemIds: List<Long>
    )

    companion object {
        fun coalesceMutations(items: List<SyncQueueEntity>): CoalescedMutations {
            if (items.size <= 1) {
                return CoalescedMutations(items, emptyList())
            }

            val droppedIds = mutableSetOf<Long>()
            val itemsByEntity = items.groupBy { it.entityType to it.entityId }

            for ((key, entityItems) in itemsByEntity) {
                if (entityItems.size <= 1) continue

                // Check if CREATE followed by DELETE on local-only entity (entityId <= 0)
                val hasCreate = entityItems.any { it.operation == "CREATE" }
                val lastOp = entityItems.last()
                if (key.second <= 0L && hasCreate && lastOp.operation == "DELETE") {
                    // Entire lifecycle was local and then deleted; drop all operations for this entity
                    droppedIds.addAll(entityItems.map { it.id })
                    continue
                }

                // If the last operation is DELETE, any preceding UPDATEs can be dropped
                if (lastOp.operation == "DELETE") {
                    for (i in 0 until entityItems.size - 1) {
                        val item = entityItems[i]
                        if (item.operation == "UPDATE") {
                            droppedIds.add(item.id)
                        }
                    }
                }

                // Coalesce multiple UPDATEs: any UPDATE followed by another UPDATE is superseded
                var lastUpdateIndex = -1
                for (i in entityItems.indices.reversed()) {
                    if (entityItems[i].operation == "UPDATE") {
                        lastUpdateIndex = i
                        break
                    }
                }
                if (lastUpdateIndex != -1) {
                    for (i in 0 until lastUpdateIndex) {
                        if (entityItems[i].operation == "UPDATE") {
                            droppedIds.add(entityItems[i].id)
                        }
                    }
                }
            }

            val surviving = items.filter { it.id !in droppedIds }
            return CoalescedMutations(surviving, droppedIds.toList())
        }
    }
}
