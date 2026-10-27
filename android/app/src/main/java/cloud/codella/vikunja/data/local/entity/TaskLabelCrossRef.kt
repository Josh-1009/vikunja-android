package cloud.codella.vikunja.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "task_label_cross_ref",
    primaryKeys = ["taskId", "labelId"],
    indices = [Index(value = ["labelId"])]
)
data class TaskLabelCrossRef(
    val taskId: Long,
    val labelId: Long
)
