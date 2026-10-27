package cloud.codella.vikunja.data.local.dao

import androidx.room.*
import cloud.codella.vikunja.data.local.entity.SyncQueueEntity

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun getPendingSyncItems(): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun dequeue(id: Long)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Long)

    @Query("UPDATE sync_queue SET entityId = :newId WHERE entityType = :entityType AND entityId = :oldId")
    suspend fun updateEntityIdInQueue(entityType: String, oldId: Long, newId: Long)

    @Query("DELETE FROM sync_queue WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun deleteByEntity(entityType: String, entityId: Long)

    @Query("SELECT entityId FROM sync_queue WHERE entityType = 'TASK'")
    suspend fun getPendingTaskIds(): List<Long>

    @Query("DELETE FROM sync_queue")
    suspend fun clearAll()
}
