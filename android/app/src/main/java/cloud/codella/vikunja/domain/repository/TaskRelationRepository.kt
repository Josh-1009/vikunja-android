package cloud.codella.vikunja.domain.repository

import cloud.codella.vikunja.domain.model.RelationKind
import cloud.codella.vikunja.domain.model.TaskRelation
import kotlinx.coroutines.flow.Flow

interface TaskRelationRepository {
    fun getRelationsForTaskFlow(taskId: Long): Flow<List<TaskRelation>>
    fun getBlockedTaskIdsFlow(): Flow<Set<Long>>
    suspend fun addRelation(taskId: Long, targetTaskId: Long, relationKind: RelationKind, targetTaskTitle: String)
    suspend fun deleteRelation(taskId: Long, targetTaskId: Long, relationKind: RelationKind)
}
