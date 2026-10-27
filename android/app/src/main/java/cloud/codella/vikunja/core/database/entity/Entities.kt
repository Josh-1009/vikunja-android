package cloud.codella.vikunja.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Aliases forwarding to the Clean Architecture data.local layer
typealias ProjectEntity = cloud.codella.vikunja.data.local.entity.ProjectEntity
typealias TaskEntity = cloud.codella.vikunja.data.local.entity.TaskEntity
typealias LabelEntity = cloud.codella.vikunja.data.local.entity.LabelEntity
typealias TaskLabelCrossRef = cloud.codella.vikunja.data.local.entity.TaskLabelCrossRef
typealias SyncQueueEntity = cloud.codella.vikunja.data.local.entity.SyncQueueEntity

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val subject: String,
    val read: Boolean,
    val created: String
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val hexColor: String = "1973E8",
    val iconName: String = "Check",
    val streak: Int = 0,
    val lastCompletedDate: String = "",
    val created: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "habit_logs",
    primaryKeys = ["habitId", "date"]
)
data class HabitLogEntity(
    val habitId: Long,
    val date: String // "YYYY-MM-DD"
)
