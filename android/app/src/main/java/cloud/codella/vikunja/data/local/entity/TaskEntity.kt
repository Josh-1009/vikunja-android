package cloud.codella.vikunja.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["projectId"]),
        Index(value = ["dueDate"]),
        Index(value = ["done"]),
        Index(value = ["isMyDay"]),
        Index(value = ["myDayDate"]),
        Index(value = ["projectId", "done", "position"]),
        Index(value = ["dueDate", "done"]),
        Index(value = ["isMyDay", "myDayDate", "done"])
    ]
)
data class TaskEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val description: String = "",
    val projectId: Long,
    val done: Boolean = false,
    val doneAt: String? = null,
    val dueDate: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val priority: Int = 0,
    val repeatAfter: Long = 0L,
    val repeatMode: Int = 0,
    val percentDone: Double = 0.0,
    val hexColor: String = "",
    val isFavorite: Boolean = false,
    val bucketId: Long = 0L,
    val position: Double = 0.0,
    val created: String = "",
    val updated: String = "",
    val createdById: Long = 0L,
    val createdByUsername: String = "",
    val assigneeIds: String = "",
    val assigneeUsernames: String = "",
    val isMyDay: Boolean = false,
    val myDayDate: String? = null
)
