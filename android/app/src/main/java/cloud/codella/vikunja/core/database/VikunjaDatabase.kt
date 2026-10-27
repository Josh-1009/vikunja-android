package cloud.codella.vikunja.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import cloud.codella.vikunja.core.database.dao.HabitDao
import cloud.codella.vikunja.core.database.dao.NotificationDao
import cloud.codella.vikunja.core.database.entity.HabitEntity
import cloud.codella.vikunja.core.database.entity.HabitLogEntity
import cloud.codella.vikunja.core.database.entity.NotificationEntity
import cloud.codella.vikunja.data.local.dao.*
import cloud.codella.vikunja.data.local.entity.*

@Database(
    entities = [
        ProjectEntity::class,
        TaskEntity::class,
        LabelEntity::class,
        TaskLabelCrossRef::class,
        NotificationEntity::class,
        SyncQueueEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        TaskRelationEntity::class,
        cloud.codella.vikunja.data.local.fts.TaskFtsEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class VikunjaDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun taskDao(): TaskDao
    abstract fun labelDao(): LabelDao
    abstract fun taskLabelCrossRefDao(): TaskLabelCrossRefDao
    abstract fun notificationDao(): NotificationDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun habitDao(): HabitDao
    abstract fun taskRelationDao(): TaskRelationDao

    companion object {
        @Volatile
        private var INSTANCE: VikunjaDatabase? = null

        fun getInstance(context: Context): VikunjaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VikunjaDatabase::class.java,
                    "vikunja_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
