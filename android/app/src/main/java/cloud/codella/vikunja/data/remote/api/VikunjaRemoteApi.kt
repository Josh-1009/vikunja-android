package cloud.codella.vikunja.data.remote.api

import cloud.codella.vikunja.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface VikunjaRemoteApi {

    @GET("api/v1/info")
    suspend fun getInfo(): RemoteServerInfoDto

    @POST("api/v1/login")
    suspend fun login(@Body request: RemoteLoginRequest): TokenResponseDto

    @POST("api/v1/user/token/refresh")
    suspend fun refreshToken(): TokenResponseDto

    @GET("api/v1/user")
    suspend fun getCurrentUser(): RemoteUserDto

    @POST("api/v1/user/logout")
    suspend fun logout(): Response<Unit>

    // Projects
    @GET("api/v1/projects")
    suspend fun getProjects(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): List<RemoteProjectDto>

    @GET("api/v1/projects/{id}")
    suspend fun getProject(@Path("id") id: Long): RemoteProjectDto

    @PUT("api/v1/projects")
    suspend fun createProject(@Body request: RemoteProjectMutationRequest): RemoteProjectDto

    @POST("api/v1/projects/{id}")
    suspend fun updateProject(
        @Path("id") id: Long,
        @Body request: RemoteProjectMutationRequest
    ): RemoteProjectDto

    @DELETE("api/v1/projects/{id}")
    suspend fun deleteProject(@Path("id") id: Long): Response<Unit>

    // Tasks
    @GET("api/v1/tasks")
    suspend fun getAllTasks(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50,
        @Query("sort_by") sortBy: String? = null,
        @Query("order_by") orderBy: String? = null
    ): List<RemoteTaskDto>

    @GET("api/v1/projects/{projectId}/tasks")
    suspend fun getProjectTasks(
        @Path("projectId") projectId: Long,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 50
    ): List<RemoteTaskDto>

    @GET("api/v1/tasks/{id}")
    suspend fun getTask(@Path("id") id: Long): RemoteTaskDto

    @PUT("api/v1/projects/{projectId}/tasks")
    suspend fun createTask(
        @Path("projectId") projectId: Long,
        @Body request: RemoteTaskMutationRequest
    ): RemoteTaskDto

    @POST("api/v1/tasks/{id}")
    suspend fun updateTask(
        @Path("id") id: Long,
        @Body request: RemoteTaskMutationRequest
    ): RemoteTaskDto

    @DELETE("api/v1/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: Long): Response<Unit>

    // Relations
    @PUT("api/v1/tasks/{taskId}/relations")
    suspend fun addRelation(
        @Path("taskId") taskId: Long,
        @Body relation: RemoteTaskRelationDto
    ): Response<Unit>

    @DELETE("api/v1/tasks/{taskId}/relations/{relationKind}/{otherTaskId}")
    suspend fun deleteRelation(
        @Path("taskId") taskId: Long,
        @Path("relationKind") relationKind: String,
        @Path("otherTaskId") otherTaskId: Long
    ): Response<Unit>
}
