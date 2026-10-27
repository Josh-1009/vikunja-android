package cloud.codella.vikunja.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cloud.codella.vikunja.data.local.entity.TaskRelationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskRelationDao {

    @Query("SELECT * FROM task_relations WHERE taskId = :taskId")
    fun getRelationsForTaskFlow(taskId: Long): Flow<List<TaskRelationEntity>>

    @Query("SELECT * FROM task_relations WHERE taskId = :taskId")
    suspend fun getRelationsForTask(taskId: Long): List<TaskRelationEntity>

    @Query("""
        SELECT DISTINCT r.taskId FROM task_relations r
        INNER JOIN tasks t ON r.targetTaskId = t.id
        WHERE r.relationKind = 'blocked' AND t.done = 0
    """)
    fun getBlockedTaskIdsFlow(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelations(relations: List<TaskRelationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelation(relation: TaskRelationEntity)

    @Query("DELETE FROM task_relations WHERE taskId = :taskId AND targetTaskId = :targetTaskId AND relationKind = :relationKind")
    suspend fun deleteRelation(taskId: Long, targetTaskId: Long, relationKind: String)

    @Query("DELETE FROM task_relations WHERE taskId = :taskId OR targetTaskId = :taskId")
    suspend fun deleteRelationsForTask(taskId: Long)

    @Query("DELETE FROM task_relations")
    suspend fun clearAll()
}
