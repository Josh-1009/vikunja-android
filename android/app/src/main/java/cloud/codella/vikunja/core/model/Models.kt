package cloud.codella.vikunja.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerInfo(
    val version: String = "",
    @SerialName("frontend_url") val frontendUrl: String = "",
    val motd: String = "",
    @SerialName("totp_enabled") val totpEnabled: Boolean = false,
    @SerialName("task_attachments_enabled") val taskAttachmentsEnabled: Boolean = true,
    @SerialName("task_comments_enabled") val taskCommentsEnabled: Boolean = true,
    @SerialName("webhooks_enabled") val webhooksEnabled: Boolean = true,
    @SerialName("link_sharing_enabled") val linkSharingEnabled: Boolean = true,
    @SerialName("caldav_enabled") val caldavEnabled: Boolean = true,
    @SerialName("max_file_size") val maxFileSize: String = "20MB"
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    @SerialName("totp_passcode") val totpPasscode: String? = null,
    @SerialName("long_token") val longToken: Boolean = true
)

@Serializable
data class LoginResponse(
    val token: String
)

@Serializable
data class User(
    val id: Long = 0,
    val username: String = "",
    val name: String = "",
    val email: String = "",
    @SerialName("is_admin") val isAdmin: Boolean = false,
    val timezone: String = "UTC",
    @SerialName("default_project_id") val defaultProjectId: Long = 0,
    @SerialName("created") val created: String = ""
)

@Serializable
data class Project(
    val id: Long = 0,
    val title: String = "",
    val description: String? = "",
    val identifier: String? = "",
    @SerialName("hex_color") val hexColor: String? = "",
    val owner: User? = null,
    @SerialName("owner_id") val ownerId: Long = 0,
    @SerialName("parent_project_id") val parentProjectId: Long? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    val position: Double? = 0.0,
    val created: String? = "",
    val updated: String? = "",
    val views: List<ProjectView>? = emptyList()
)

@Serializable
data class ProjectCreateUpdateRequest(
    val title: String,
    val description: String = "",
    @SerialName("hex_color") val hexColor: String = "",
    @SerialName("parent_project_id") val parentProjectId: Long? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("is_favorite") val isFavorite: Boolean = false
)

@Serializable
data class ProjectView(
    val id: Long = 0,
    val title: String = "",
    @SerialName("project_id") val projectId: Long = 0,
    @SerialName("view_kind") val viewKind: String = "list", // list, kanban, gantt, table
    val position: Double? = 0.0,
    @SerialName("bucket_configuration_mode") val bucketConfigurationMode: String? = "none",
    @SerialName("default_bucket_id") val defaultBucketId: Long? = 0
)

@Serializable
data class Bucket(
    val id: Long = 0,
    val title: String = "",
    @SerialName("project_view_id") val projectViewId: Long = 0,
    val limit: Long = 0,
    val position: Double = 0.0,
    val tasks: List<Task> = emptyList()
)

@Serializable
data class BucketCreateRequest(
    val title: String,
    val limit: Long = 0
)

@Serializable
data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    @SerialName("project_id") val projectId: Long,
    val done: Boolean = false,
    @SerialName("done_at") val doneAt: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val priority: Int = 0,
    @SerialName("repeat_after") val repeatAfter: Long = 0,
    @SerialName("repeat_mode") val repeatMode: Int = 0, // 0=none, 1=daily, 2=weekly, 3=monthly, 4=yearly
    @SerialName("percent_done") val percentDone: Double = 0.0,
    @SerialName("hex_color") val hexColor: String = "",
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    val index: Long = 0,
    val labels: List<Label> = emptyList(),
    val assignees: List<User> = emptyList(),
    val reminders: List<TaskReminder> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    @SerialName("bucket_id") val bucketId: Long = 0,
    val position: Double = 0.0,
    val created: String = "",
    val updated: String = "",
    @SerialName("created_by") val createdBy: User? = null
)

@Serializable
data class TaskCreateUpdateRequest(
    val title: String? = null,
    val description: String? = null,
    val done: Boolean? = null,
    @SerialName("project_id") val projectId: Long? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val priority: Int? = null,
    @SerialName("repeat_after") val repeatAfter: Long? = null,
    @SerialName("repeat_mode") val repeatMode: Int? = null,
    @SerialName("percent_done") val percentDone: Double? = null,
    @SerialName("hex_color") val hexColor: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean? = null
)

@Serializable
data class TaskReminder(
    @SerialName("reminder") val reminder: String, // ISO timestamp
    @SerialName("relative_period") val relativePeriod: Long = 0,
    @SerialName("relative_to") val relativeTo: String = "" // "due_date", "start_date", "end_date"
)

@Serializable
data class Label(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    @SerialName("hex_color") val hexColor: String = "1973ff",
    @SerialName("created_by") val createdBy: User? = null
)

@Serializable
data class TaskComment(
    val id: Long = 0,
    val comment: String,
    val author: User? = null,
    val created: String = "",
    val updated: String = ""
)

@Serializable
data class TaskCommentRequest(
    val comment: String
)

@Serializable
data class Attachment(
    val id: Long = 0,
    val name: String = "",
    val size: Long = 0,
    @SerialName("created_by") val createdBy: User? = null,
    val created: String = ""
)

@Serializable
data class TaskRelation(
    @SerialName("other_task_id") val otherTaskId: Long,
    @SerialName("relation_kind") val relationKind: String, // subtask, parenttask, related, blocking, blocked, duplicate, precedes, follows, copied
    @SerialName("task_id") val taskId: Long = 0
)

@Serializable
data class NotificationItem(
    val id: Long = 0,
    val name: String = "",
    val subject: String = "",
    val read: Boolean = false,
    val created: String = ""
)

@Serializable
data class ApiToken(
    val id: Long = 0,
    val title: String,
    val token: String = "",
    val permissions: Map<String, String> = emptyMap(),
    @SerialName("expires_at") val expiresAt: String? = null,
    val created: String = ""
)

@Serializable
data class ApiTokenCreateRequest(
    val title: String,
    @SerialName("expires_at") val expiresAt: String? = null
)

@Serializable
data class Webhook(
    val id: Long = 0,
    @SerialName("target_url") val targetUrl: String,
    @SerialName("events") val events: List<String> = emptyList(),
    val secret: String = ""
)

@Serializable
data class WebhookCreateRequest(
    @SerialName("target_url") val targetUrl: String,
    @SerialName("events") val events: List<String> = emptyList()
)

@Serializable
data class MoveTaskToBucketRequest(
    @SerialName("task_id") val taskId: Long
)

@Serializable
data class BulkTaskActionRequest(
    val action: String, // "delete", "done", "undone"
    @SerialName("task_ids") val taskIds: List<Long>
)
