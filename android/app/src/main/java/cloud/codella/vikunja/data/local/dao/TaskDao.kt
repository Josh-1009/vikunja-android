package cloud.codella.vikunja.data.local.dao

import androidx.room.*
import cloud.codella.vikunja.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY position ASC, id DESC")
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE projectId = :projectId ORDER BY position ASC, id DESC")
    fun getTasksByProjectFlow(projectId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun getTaskByIdFlow(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE dueDate IS NOT NULL AND done = 0 ORDER BY dueDate ASC")
    suspend fun getTasksWithDueDate(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET done = :done WHERE id = :id")
    suspend fun updateTaskDone(id: Long, done: Boolean)

    @Query("UPDATE tasks SET done = :done, doneAt = :doneAt, updated = :updated WHERE id = :id")
    suspend fun updateTaskDone(id: Long, done: Boolean, doneAt: String?, updated: String)

    @Query("UPDATE tasks SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateTaskFavorite(id: Long, isFavorite: Boolean)

    @Query("SELECT * FROM tasks WHERE isMyDay = 1 AND myDayDate = :todayDate ORDER BY position ASC, id DESC")
    fun getMyDayTasksFlow(todayDate: String): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET isMyDay = :isMyDay, myDayDate = :myDayDate WHERE id = :id")
    suspend fun updateTaskMyDay(id: Long, isMyDay: Boolean, myDayDate: String?)

    @Query("UPDATE tasks SET isMyDay = 0, myDayDate = NULL WHERE myDayDate IS NOT NULL AND myDayDate < :todayDate")
    suspend fun resetPastMyDayTasks(todayDate: String)

    @Query("""
        SELECT tasks.* FROM tasks
        JOIN tasks_fts ON tasks.id = tasks_fts.docid
        WHERE tasks_fts MATCH :query AND tasks.done = 0
        ORDER BY tasks.priority DESC, tasks.id DESC
    """)
    fun searchTasksFts(query: String): Flow<List<TaskEntity>>

    fun searchTasksWithPrefix(rawQuery: String): Flow<List<TaskEntity>> {
        val sanitized = rawQuery.trim().replace(Regex("[^a-zA-Z0-9\\s]"), "")
        if (sanitized.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        val ftsQuery = sanitized.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
        return searchTasksFts(ftsQuery)
    }

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Query("DELETE FROM tasks WHERE projectId = :projectId")
    suspend fun deleteTasksByProject(projectId: Long)

    @Query("DELETE FROM tasks")
    suspend fun clearAll()

    @Transaction
    suspend fun reconcileTaskId(oldId: Long, newId: Long, updatedServerTask: TaskEntity) {
        deleteTask(oldId)
        insertTask(updatedServerTask.copy(id = newId))
    }
}
