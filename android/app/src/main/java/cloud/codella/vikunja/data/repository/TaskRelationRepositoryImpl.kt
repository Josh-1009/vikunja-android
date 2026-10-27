package cloud.codella.vikunja.data.repository

import cloud.codella.vikunja.data.local.dao.SyncQueueDao
import cloud.codella.vikunja.data.local.dao.TaskRelationDao
import cloud.codella.vikunja.data.local.entity.SyncQueueEntity
import cloud.codella.vikunja.data.local.entity.TaskRelationEntity
import cloud.codella.vikunja.data.remote.api.VikunjaRemoteApi
import cloud.codella.vikunja.data.remote.dto.RemoteTaskRelationDto
import cloud.codella.vikunja.domain.model.RelationKind
import cloud.codella.vikunja.domain.model.TaskRelation
import cloud.codella.vikunja.domain.repository.TaskRelationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TaskRelationRepositoryImpl(
    private val taskRelationDao: TaskRelationDao,
    private val syncQueueDao: SyncQueueDao,
    private val api: VikunjaRemoteApi? = null,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : TaskRelationRepository {

    override fun getRelationsForTaskFlow(taskId: Long): Flow<List<TaskRelation>> {
        return taskRelationDao.getRelationsForTaskFlow(taskId).map { entities ->
            entities.map { entity ->
                TaskRelation(
                    taskId = entity.taskId,
                    targetTaskId = entity.targetTaskId,
                    relationKind = RelationKind.fromApiValue(entity.relationKind),
                    targetTaskTitle = entity.targetTaskTitle
                )
            }
        }
    }

    override fun getBlockedTaskIdsFlow(): Flow<Set<Long>> {
        return taskRelationDao.getBlockedTaskIdsFlow().map { it.toSet() }
    }

    override suspend fun addRelation(
        taskId: Long,
        targetTaskId: Long,
        relationKind: RelationKind,
        targetTaskTitle: String
    ) {
        val entity = TaskRelationEntity(
            taskId = taskId,
            targetTaskId = targetTaskId,
            relationKind = relationKind.apiValue,
            targetTaskTitle = targetTaskTitle
        )
        taskRelationDao.insertRelation(entity)

        val dto = RemoteTaskRelationDto(
            taskId = taskId,
            otherTaskId = targetTaskId,
            relationKind = relationKind.apiValue
        )
        val payload = json.encodeToString(dto)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "TASK_RELATION",
                entityId = taskId,
                operation = "CREATE",
                payloadJson = payload,
                createdAt = System.currentTimeMillis()
            )
        )

        // Try direct API if available
        api?.let {
            runCatching {
                it.addRelation(taskId, dto)
            }
        }
    }

    override suspend fun deleteRelation(
        taskId: Long,
        targetTaskId: Long,
        relationKind: RelationKind
    ) {
        taskRelationDao.deleteRelation(taskId, targetTaskId, relationKind.apiValue)

        val dto = RemoteTaskRelationDto(
            taskId = taskId,
            otherTaskId = targetTaskId,
            relationKind = relationKind.apiValue
        )
        val payload = json.encodeToString(dto)
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "TASK_RELATION",
                entityId = taskId,
                operation = "DELETE",
                payloadJson = payload,
                createdAt = System.currentTimeMillis()
            )
        )

        // Try direct API if available
        api?.let {
            runCatching {
                it.deleteRelation(taskId, relationKind.apiValue, targetTaskId)
            }
        }
    }
}
