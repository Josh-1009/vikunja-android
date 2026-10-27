package cloud.codella.vikunja.core.network

import cloud.codella.vikunja.core.model.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface VikunjaApiService {

    // Info & Auth
    @GET("api/v1/info")
    suspend fun getServerInfo(): ServerInfo

    @POST("api/v1/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/v1/user/token/refresh")
    suspend fun refreshToken(): LoginResponse

    @GET("api/v1/user")
    suspend fun getCurrentUser(): User

    @POST("api/v1/user/logout")
    suspend fun logout(): Response<Unit>

    // Projects
    @GET("api/v1/projects")
    suspend fun getProjects(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): List<Project>

    @GET("api/v1/projects/{id}")
    suspend fun getProject(@Path("id") id: Long): Project

    @PUT("api/v1/projects")
    suspend fun createProject(@Body request: ProjectCreateUpdateRequest): Project

    @POST("api/v1/projects/{id}")
    suspend fun updateProject(
        @Path("id") id: Long,
        @Body request: ProjectCreateUpdateRequest
    ): Project

    @DELETE("api/v1/projects/{id}")
    suspend fun deleteProject(@Path("id") id: Long): Response<Unit>

    @PUT("api/v1/projects/{id}/duplicate")
    suspend fun duplicateProject(@Path("id") id: Long): Project

    // Views & Buckets (Kanban, etc.)
    @GET("api/v1/projects/{projectId}/views")
    suspend fun getProjectViews(@Path("projectId") projectId: Long): List<ProjectView>

    @GET("api/v1/projects/{projectId}/views/{viewId}/buckets")
    suspend fun getBuckets(
        @Path("projectId") projectId: Long,
        @Path("viewId") viewId: Long
    ): List<Bucket>

    @PUT("api/v1/projects/{projectId}/views/{viewId}/buckets")
    suspend fun createBucket(
        @Path("projectId") projectId: Long,
        @Path("viewId") viewId: Long,
        @Body request: BucketCreateRequest
    ): Bucket

    @DELETE("api/v1/projects/{projectId}/views/{viewId}/buckets/{bucketId}")
    suspend fun deleteBucket(
        @Path("projectId") projectId: Long,
        @Path("viewId") viewId: Long,
        @Path("bucketId") bucketId: Long
    ): Response<Unit>

    @POST("api/v1/projects/{projectId}/views/{viewId}/buckets/{bucketId}/tasks")
    suspend fun moveTaskToBucket(
        @Path("projectId") projectId: Long,
        @Path("viewId") viewId: Long,
        @Path("bucketId") bucketId: Long,
        @Body request: MoveTaskToBucketRequest
    ): Response<Unit>

    // Tasks
    @GET("api/v1/tasks")
    suspend fun getAllTasks(
        @Query("filter") filter: String? = null,
        @Query("sort_by") sortBy: String? = null,
        @Query("order_by") orderBy: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): List<Task>

    @GET("api/v1/projects/{projectId}/tasks")
    suspend fun getProjectTasks(
        @Path("projectId") projectId: Long,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): List<Task>

    @GET("api/v1/tasks/{id}")
    suspend fun getTask(@Path("id") id: Long): Task

    @PUT("api/v1/projects/{projectId}/tasks")
    suspend fun createTask(
        @Path("projectId") projectId: Long,
        @Body request: TaskCreateUpdateRequest
    ): Task

    @POST("api/v1/tasks/{id}")
    suspend fun updateTask(
        @Path("id") id: Long,
        @Body request: TaskCreateUpdateRequest
    ): Task

    @DELETE("api/v1/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: Long): Response<Unit>

    @PUT("api/v1/tasks/{id}/duplicate")
    suspend fun duplicateTask(@Path("id") id: Long): Task

    // Task Comments
    @GET("api/v1/tasks/{taskId}/comments")
    suspend fun getTaskComments(@Path("taskId") taskId: Long): List<TaskComment>

    @PUT("api/v1/tasks/{taskId}/comments")
    suspend fun addTaskComment(
        @Path("taskId") taskId: Long,
        @Body request: TaskCommentRequest
    ): TaskComment

    @DELETE("api/v1/tasks/{taskId}/comments/{commentId}")
    suspend fun deleteTaskComment(
        @Path("taskId") taskId: Long,
        @Path("commentId") commentId: Long
    ): Response<Unit>

    // Task Attachments
    @GET("api/v1/tasks/{taskId}/attachments")
    suspend fun getTaskAttachments(@Path("taskId") taskId: Long): List<Attachment>

    @Multipart
    @PUT("api/v1/tasks/{taskId}/attachments")
    suspend fun uploadTaskAttachment(
        @Path("taskId") taskId: Long,
        @Part file: MultipartBody.Part
    ): Attachment

    @GET("api/v1/tasks/{taskId}/attachments/{attachmentId}")
    @Streaming
    suspend fun downloadAttachment(
        @Path("taskId") taskId: Long,
        @Path("attachmentId") attachmentId: Long
    ): ResponseBody

    @DELETE("api/v1/tasks/{taskId}/attachments/{attachmentId}")
    suspend fun deleteTaskAttachment(
        @Path("taskId") taskId: Long,
        @Path("attachmentId") attachmentId: Long
    ): Response<Unit>

    // Task Relations & Subtasks
    @PUT("api/v1/tasks/{taskId}/relations")
    suspend fun addRelation(
        @Path("taskId") taskId: Long,
        @Body relation: TaskRelation
    ): Response<Unit>

    @DELETE("api/v1/tasks/{taskId}/relations/{relationKind}/{otherTaskId}")
    suspend fun deleteRelation(
        @Path("taskId") taskId: Long,
        @Path("relationKind") relationKind: String,
        @Path("otherTaskId") otherTaskId: Long
    ): Response<Unit>

    // Labels
    @GET("api/v1/labels")
    suspend fun getLabels(): List<Label>

    @PUT("api/v1/labels")
    suspend fun createLabel(@Body label: Label): Label

    @PUT("api/v1/tasks/{taskId}/labels")
    suspend fun assignLabel(
        @Path("taskId") taskId: Long,
        @Body label: Label
    ): Response<Unit>

    @DELETE("api/v1/tasks/{taskId}/labels/{labelId}")
    suspend fun removeLabel(
        @Path("taskId") taskId: Long,
        @Path("labelId") labelId: Long
    ): Response<Unit>

    // Notifications
    @GET("api/v1/notifications")
    suspend fun getNotifications(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): List<NotificationItem>

    @POST("api/v1/notifications/{id}")
    suspend fun markNotificationRead(@Path("id") id: Long): Response<Unit>

    @DELETE("api/v1/notifications")
    suspend fun clearNotifications(): Response<Unit>

    // Tokens & Webhooks
    @GET("api/v1/tokens")
    suspend fun getApiTokens(): List<ApiToken>

    @PUT("api/v1/tokens")
    suspend fun createApiToken(@Body request: ApiTokenCreateRequest): ApiToken

    @DELETE("api/v1/tokens/{id}")
    suspend fun deleteApiToken(@Path("id") id: Long): Response<Unit>

    @GET("api/v1/user/settings/webhooks")
    suspend fun getWebhooks(): List<Webhook>

    @PUT("api/v1/user/settings/webhooks")
    suspend fun createWebhook(@Body request: WebhookCreateRequest): Webhook

    @DELETE("api/v1/user/settings/webhooks/{id}")
    suspend fun deleteWebhook(@Path("id") id: Long): Response<Unit>
}
