package cloud.codella.vikunja.data.local.dao

import androidx.room.*
import cloud.codella.vikunja.data.local.entity.LabelEntity
import cloud.codella.vikunja.data.local.entity.TaskLabelCrossRef

@Dao
interface TaskLabelCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(crossRef: TaskLabelCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(crossRefs: List<TaskLabelCrossRef>)

    @Query("DELETE FROM task_label_cross_ref WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: Long)

    @Query("DELETE FROM task_label_cross_ref WHERE taskId = :taskId AND labelId = :labelId")
    suspend fun delete(taskId: Long, labelId: Long)

    @Query("""
        SELECT l.* FROM labels l
        INNER JOIN task_label_cross_ref x ON l.id = x.labelId
        WHERE x.taskId = :taskId
    """)
    suspend fun getLabelsForTask(taskId: Long): List<LabelEntity>
}
