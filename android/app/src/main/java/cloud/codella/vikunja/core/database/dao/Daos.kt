package cloud.codella.vikunja.core.database.dao

import androidx.room.*
import cloud.codella.vikunja.core.database.entity.HabitEntity
import cloud.codella.vikunja.core.database.entity.HabitLogEntity
import cloud.codella.vikunja.core.database.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

// Aliases forwarding to the Clean Architecture data.local layer
typealias ProjectDao = cloud.codella.vikunja.data.local.dao.ProjectDao
typealias TaskDao = cloud.codella.vikunja.data.local.dao.TaskDao
typealias LabelDao = cloud.codella.vikunja.data.local.dao.LabelDao
typealias SyncQueueDao = cloud.codella.vikunja.data.local.dao.SyncQueueDao

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY id DESC")
    fun getAllNotificationsFlow(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)

    @Query("UPDATE notifications SET read = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY id ASC")
    fun getAllHabitsFlow(): Flow<List<HabitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity): Long

    @Update
    suspend fun updateHabit(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabit(id: Long)

    @Query("SELECT * FROM habit_logs WHERE date = :date")
    fun getLogsForDateFlow(date: String): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs")
    fun getAllLogsFlow(): Flow<List<HabitLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: HabitLogEntity)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND date = :date")
    suspend fun deleteLog(habitId: Long, date: String)
}
