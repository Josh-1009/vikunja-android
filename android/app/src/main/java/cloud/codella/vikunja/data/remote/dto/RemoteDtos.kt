package cloud.codella.vikunja.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponseDto(
    val token: String
)

@Serializable
data class RemoteServerInfoDto(
    val version: String = "",
    @SerialName("frontend_url") val frontendUrl: String = "",
    val motd: String = "",
    @SerialName("totp_enabled") val totpEnabled: Boolean = false,
    @SerialName("concurrent_writes") val concurrentWrites: Boolean = true
)

@Serializable
data class RemoteLoginRequest(
    val username: String,
    val password: String,
    @SerialName("totp_passcode") val totpPasscode: String? = null,
    @SerialName("long_token") val longToken: Boolean = true
)

@Serializable
data class RemoteTaskDto(
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
    @SerialName("repeat_mode") val repeatMode: Int = 0,
    @SerialName("percent_done") val percentDone: Double = 0.0,
    @SerialName("hex_color") val hexColor: String = "",
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("bucket_id") val bucketId: Long = 0,
    val position: Double = 0.0,
    val created: String = "",
    val updated: String = "",
    val labels: List<RemoteLabelDto> = emptyList(),
    val assignees: List<RemoteUserDto> = emptyList()
)

@Serializable
data class RemoteTaskMutationRequest(
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
data class RemoteProjectDto(
    val id: Long = 0,
    val title: String = "",
    val description: String? = "",
    @SerialName("hex_color") val hexColor: String? = "",
    @SerialName("owner_id") val ownerId: Long = 0,
    @SerialName("parent_project_id") val parentProjectId: Long? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    val position: Double? = 0.0,
    val updated: String? = ""
)

@Serializable
data class RemoteProjectMutationRequest(
    val title: String,
    val description: String = "",
    @SerialName("hex_color") val hexColor: String = "",
    @SerialName("parent_project_id") val parentProjectId: Long? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("is_favorite") val isFavorite: Boolean = false
)

@Serializable
data class RemoteLabelDto(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    @SerialName("hex_color") val hexColor: String = ""
)

@Serializable
data class RemoteUserDto(
    val id: Long = 0,
    val username: String = "",
    val name: String = "",
    val email: String = ""
)

@Serializable
data class RemoteTaskRelationDto(
    @SerialName("task_id") val taskId: Long = 0,
    @SerialName("other_task_id") val otherTaskId: Long,
    @SerialName("relation_kind") val relationKind: String
)

