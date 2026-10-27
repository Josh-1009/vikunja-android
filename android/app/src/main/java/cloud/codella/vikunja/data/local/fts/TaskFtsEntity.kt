package cloud.codella.vikunja.data.local.fts

import androidx.room.Entity
import androidx.room.Fts4
import cloud.codella.vikunja.data.local.entity.TaskEntity

@Entity(tableName = "tasks_fts")
@Fts4(contentEntity = TaskEntity::class)
data class TaskFtsEntity(
    val title: String,
    val description: String
)
