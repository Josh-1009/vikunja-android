package cloud.codella.vikunja.domain.model

enum class RelationKind(val apiValue: String, val displayName: String) {
    BLOCKS("blocking", "Blocks"),
    BLOCKED_BY("blocked", "Blocked by"),
    DUPLICATES("duplicate", "Duplicates"),
    PARENT_TO("parenttask", "Parent of"),
    CHILD_OF("subtask", "Subtask of"),
    RELATED("related", "Related to");

    companion object {
        fun fromApiValue(value: String): RelationKind =
            entries.firstOrNull { it.apiValue.equals(value, ignoreCase = true) } ?: RELATED
    }
}

data class TaskRelation(
    val taskId: Long,
    val targetTaskId: Long,
    val relationKind: RelationKind,
    val targetTaskTitle: String = ""
)
