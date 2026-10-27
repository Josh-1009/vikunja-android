package cloud.codella.vikunja.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "task_relations",
    primaryKeys = ["taskId", "targetTaskId", "relationKind"],
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["targetTaskId"]),
        Index(value = ["relationKind"]),
        Index(value = ["taskId", "relationKind"])
    ]
)
data class TaskRelationEntity(
    val taskId: Long,
    val targetTaskId: Long,
    val relationKind: String,
    val targetTaskTitle: String = ""
)
