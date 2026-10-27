package cloud.codella.vikunja.domain.model

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE
}

enum class EntityType {
    TASK,
    PROJECT,
    LABEL
}

data class SyncOperation(
    val id: Long = 0L,
    val entityType: EntityType,
    val entityId: Long,
    val operation: OperationType,
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)

sealed interface SyncResult {
    data class Success(val pushedCount: Int, val pulledCount: Int) : SyncResult
    data class Partial(val pushedCount: Int, val pulledCount: Int, val errors: List<String>) : SyncResult
    data class Failure(val cause: Throwable) : SyncResult
}
