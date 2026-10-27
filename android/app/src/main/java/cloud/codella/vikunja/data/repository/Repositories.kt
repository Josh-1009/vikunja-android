package cloud.codella.vikunja.data.repository

import android.content.Context
import cloud.codella.vikunja.core.database.VikunjaDatabase
import cloud.codella.vikunja.core.database.entity.*
import cloud.codella.vikunja.core.datastore.PreferencesManager
import cloud.codella.vikunja.core.datastore.TokenManager
import cloud.codella.vikunja.core.model.*
import cloud.codella.vikunja.core.network.NetworkClient
import cloud.codella.vikunja.core.network.VikunjaApiService
import cloud.codella.vikunja.core.notifications.AlarmScheduler
import cloud.codella.vikunja.core.notifications.NotificationHelper
import cloud.codella.vikunja.core.util.AdvancedNlpParser
import cloud.codella.vikunja.core.util.DateUtils
import cloud.codella.vikunja.core.util.QuickAddParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MultipartBody

class AuthRepository(
    private val networkClient: NetworkClient,
    private val tokenManager: TokenManager,
    private val preferencesManager: PreferencesManager,
    private val database: VikunjaDatabase
) {
    suspend fun getServerInfo(): ServerInfo {
        return networkClient.getApiService().getServerInfo()
    }

    private fun normalizeServerUrl(url: String): String {
        var trimmed = url.trim().trimEnd('/')
        if (trimmed.endsWith("/api/v1")) {
            trimmed = trimmed.removeSuffix("/api/v1").trimEnd('/')
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        return trimmed
    }

    suspend fun loginWithCredentials(
        serverUrl: String,
        username: String,
        pass: String,
        totp: String? = null,
        allowSelfSigned: Boolean = false
    ): Result<Unit> {
        val cleanUrl = normalizeServerUrl(serverUrl)
        return try {
            preferencesManager.setServerUrl(cleanUrl)
            preferencesManager.setAllowSelfSigned(allowSelfSigned)
            networkClient.invalidate()

            val api = networkClient.getApiService()
            val resp = api.login(
                LoginRequest(
                    username = username.trim(),
                    password = pass,
                    totpPasscode = totp?.trim()?.ifBlank { null },
                    longToken = true
                )
            )
            tokenManager.saveToken(resp.token, isApiToken = false)

            try {
                val user = api.getCurrentUser()
                preferencesManager.setActiveUser(user.id, user.username)
            } catch (_: Exception) {
                preferencesManager.setActiveUser(0L, username.trim())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithApiToken(
        serverUrl: String,
        token: String,
        allowSelfSigned: Boolean = false
    ): Result<Unit> {
        val cleanUrl = normalizeServerUrl(serverUrl)
        val cleanToken = if (token.startsWith("Bearer ", ignoreCase = true)) {
            token.substring(7).trim()
        } else {
            token.trim()
        }
        return try {
            preferencesManager.setServerUrl(cleanUrl)
            preferencesManager.setAllowSelfSigned(allowSelfSigned)
            tokenManager.saveToken(cleanToken, isApiToken = true)
            networkClient.invalidate()

            val api = networkClient.getApiService()
            try {
                val user = api.getCurrentUser()
                preferencesManager.setActiveUser(user.id, user.username)
            } catch (_: Exception) {
                preferencesManager.setActiveUser(0L, "API Token User")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        try {
            networkClient.getApiService().logout()
        } catch (_: Exception) {}
        tokenManager.clear()
        preferencesManager.clearUser()
        database.clearAllTables()
    }

    fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()
}

class ProjectRepository(
    private val networkClient: NetworkClient,
    private val database: VikunjaDatabase
) {
    val projectsFlow: Flow<List<ProjectEntity>> = database.projectDao().getAllProjectsFlow()

    suspend fun refreshProjects(): Result<List<Project>> {
        return try {
            val api = networkClient.getApiService()
            val allProjects = mutableListOf<Project>()
            var page = 1
            while (true) {
                val pageProjects = api.getProjects(page = page, perPage = 50)
                allProjects.addAll(pageProjects)
                if (pageProjects.size < 50) break
                page++
            }
            val entities = allProjects.map { p ->
                ProjectEntity(
                    id = p.id,
                    title = p.title,
                    description = p.description ?: "",
                    hexColor = p.hexColor ?: "",
                    ownerId = p.owner?.id ?: p.ownerId,
                    parentProjectId = if (p.parentProjectId == null || p.parentProjectId == 0L) null else p.parentProjectId,
                    isArchived = p.isArchived,
                    isFavorite = p.isFavorite,
                    position = p.position ?: 0.0,
                    updated = p.updated ?: ""
                )
            }
            database.projectDao().clearAll()
            database.projectDao().insertProjects(entities)
            Result.success(allProjects)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun createProject(request: ProjectCreateUpdateRequest): Result<Project> {
        return try {
            val p = networkClient.getApiService().createProject(request)
            database.projectDao().insertProject(
                ProjectEntity(
                    id = p.id,
                    title = p.title,
                    description = p.description ?: "",
                    hexColor = p.hexColor ?: "",
                    ownerId = p.owner?.id ?: p.ownerId,
                    parentProjectId = if (p.parentProjectId == null || p.parentProjectId == 0L) null else p.parentProjectId,
                    isArchived = p.isArchived,
                    isFavorite = p.isFavorite,
                    position = p.position ?: 0.0,
                    updated = p.updated ?: ""
                )
            )
            Result.success(p)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProject(id: Long, request: ProjectCreateUpdateRequest): Result<Project> {
        return try {
            val p = networkClient.getApiService().updateProject(id, request)
            database.projectDao().insertProject(
                ProjectEntity(
                    id = p.id,
                    title = p.title,
                    description = p.description ?: "",
                    hexColor = p.hexColor ?: "",
                    ownerId = p.owner?.id ?: p.ownerId,
                    parentProjectId = if (p.parentProjectId == null || p.parentProjectId == 0L) null else p.parentProjectId,
                    isArchived = p.isArchived,
                    isFavorite = p.isFavorite,
                    position = p.position ?: 0.0,
                    updated = p.updated ?: ""
                )
            )
            Result.success(p)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteProject(id: Long): Result<Unit> {
        return try {
            networkClient.getApiService().deleteProject(id)
            database.projectDao().deleteProject(id)
            database.taskDao().deleteTasksByProject(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun duplicateProject(id: Long): Result<Project> {
        return try {
            val p = networkClient.getApiService().duplicateProject(id)
            refreshProjects()
            Result.success(p)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class TaskRepository(
    private val networkClient: NetworkClient,
    private val database: VikunjaDatabase,
    private val alarmScheduler: AlarmScheduler
) {
    val allTasksFlow: Flow<List<TaskEntity>> = database.taskDao().getAllTasksFlow()

    fun getTasksByProjectFlow(projectId: Long): Flow<List<TaskEntity>> =
        database.taskDao().getTasksByProjectFlow(projectId)

    val todayTasksFlow: Flow<List<TaskEntity>> = allTasksFlow.map { tasks ->
        tasks.filter { t ->
            val due = DateUtils.parseIsoToMillis(t.dueDate)
            due != null && DateUtils.isToday(due)
        }
    }

    val upcomingTasksFlow: Flow<List<TaskEntity>> = allTasksFlow.map { tasks ->
        tasks.filter { t ->
            val due = DateUtils.parseIsoToMillis(t.dueDate)
            due != null && DateUtils.isUpcoming(due)
        }
    }

    val overdueTasksFlow: Flow<List<TaskEntity>> = allTasksFlow.map { tasks ->
        tasks.filter { t ->
            val due = DateUtils.parseIsoToMillis(t.dueDate)
            due != null && DateUtils.isOverdue(due) && !t.done
        }
    }

    val starredTasksFlow: Flow<List<TaskEntity>> = allTasksFlow.map { tasks ->
        tasks.filter { it.isFavorite }
    }

    suspend fun refreshTasks(projectId: Long? = null): Result<List<Task>> {
        return try {
            val api = networkClient.getApiService()
            val remoteTasks = if (projectId != null) {
                api.getProjectTasks(projectId, perPage = 50)
            } else {
                api.getAllTasks(perPage = 50)
            }

            val entities = remoteTasks.map { t ->
                TaskEntity(
                    id = t.id,
                    title = t.title,
                    description = t.description ?: "",
                    projectId = t.projectId,
                    done = t.done,
                    doneAt = t.doneAt,
                    dueDate = t.dueDate,
                    startDate = t.startDate,
                    endDate = t.endDate,
                    priority = t.priority,
                    repeatAfter = t.repeatAfter ?: 0L,
                    repeatMode = t.repeatMode ?: 0,
                    percentDone = t.percentDone ?: 0.0,
                    hexColor = t.hexColor ?: "",
                    isFavorite = t.isFavorite,
                    bucketId = t.bucketId ?: 0L,
                    position = t.position ?: 0.0,
                    created = t.created ?: "",
                    updated = t.updated ?: "",
                    createdById = t.createdBy?.id ?: 0L,
                    createdByUsername = t.createdBy?.username ?: "",
                    assigneeIds = t.assignees.joinToString(",") { it.id.toString() },
                    assigneeUsernames = t.assignees.joinToString(",") { it.username }
                )
            }
            if (projectId != null) {
                database.taskDao().deleteTasksByProject(projectId)
            } else {
                database.taskDao().clearAll()
            }
            database.taskDao().insertTasks(entities)

            // Reschedule exact alarms for due dates
            for (t in remoteTasks) {
                if (!t.done) {
                    val dueMillis = DateUtils.parseIsoToMillis(t.dueDate)
                    if (dueMillis != null && dueMillis > System.currentTimeMillis()) {
                        alarmScheduler.scheduleExactAlarm(t.id, t.title, dueMillis, isDueDate = true)
                    }
                }
            }

            Result.success(remoteTasks)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun getLocalTaskById(taskId: Long): TaskEntity? {
        return database.taskDao().getTaskById(taskId)
    }

    suspend fun getTaskDetails(taskId: Long): Result<Task> {
        return try {
            val t = networkClient.getApiService().getTask(taskId)
            Result.success(t)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTask(projectId: Long, request: TaskCreateUpdateRequest): Result<Task> {
        return try {
            val t = networkClient.getApiService().createTask(projectId, request)
            database.taskDao().insertTask(
                TaskEntity(
                    id = t.id,
                    title = t.title,
                    description = t.description,
                    projectId = t.projectId,
                    done = t.done,
                    doneAt = t.doneAt,
                    dueDate = t.dueDate,
                    startDate = t.startDate,
                    endDate = t.endDate,
                    priority = t.priority,
                    repeatAfter = t.repeatAfter,
                    repeatMode = t.repeatMode,
                    percentDone = t.percentDone,
                    hexColor = t.hexColor,
                    isFavorite = t.isFavorite,
                    bucketId = t.bucketId,
                    position = t.position,
                    created = t.created,
                    updated = t.updated,
                    createdById = t.createdBy?.id ?: 0L,
                    createdByUsername = t.createdBy?.username ?: "",
                    assigneeIds = t.assignees.joinToString(",") { it.id.toString() },
                    assigneeUsernames = t.assignees.joinToString(",") { it.username }
                )
            )
            val dueMillis = DateUtils.parseIsoToMillis(t.dueDate)
            if (dueMillis != null && dueMillis > System.currentTimeMillis()) {
                alarmScheduler.scheduleExactAlarm(t.id, t.title, dueMillis, isDueDate = true)
            }
            Result.success(t)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun quickAddTask(rawInput: String, defaultProjectId: Long): Result<Task> {
        val parsed = AdvancedNlpParser.parse(rawInput)

        // Find project if specified by ~name
        var targetProjectId = defaultProjectId
        if (!parsed.projectQuery.isNullOrBlank()) {
            val matched = database.projectDao().getAllProjects().firstOrNull {
                it.title.contains(parsed.projectQuery, ignoreCase = true)
            }
            if (matched != null) {
                targetProjectId = matched.id
            }
        }

        val request = TaskCreateUpdateRequest(
            title = parsed.cleanTitle,
            dueDate = parsed.dueDateIso,
            priority = parsed.priority ?: 0
        )
        return createTask(targetProjectId, request)
    }

    suspend fun toggleTaskDone(taskId: Long, currentDone: Boolean): Result<Unit> {
        val newDone = !currentDone
        return try {
            database.taskDao().updateTaskDone(taskId, newDone)
            if (newDone) {
                alarmScheduler.cancelAlarm(taskId, isDueDate = true)
            }
            val existing = database.taskDao().getTaskById(taskId)
            networkClient.getApiService().updateTask(taskId, TaskCreateUpdateRequest(title = existing?.title, done = newDone))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleTaskFavorite(taskId: Long, currentFavorite: Boolean): Result<Unit> {
        val newFavorite = !currentFavorite
        return try {
            database.taskDao().updateTaskFavorite(taskId, newFavorite)
            val existing = database.taskDao().getTaskById(taskId)
            networkClient.getApiService().updateTask(taskId, TaskCreateUpdateRequest(title = existing?.title, isFavorite = newFavorite))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTask(taskId: Long, request: TaskCreateUpdateRequest): Result<Task> {
        return try {
            val t = networkClient.getApiService().updateTask(taskId, request)
            database.taskDao().insertTask(
                TaskEntity(
                    id = t.id,
                    title = t.title,
                    description = t.description,
                    projectId = t.projectId,
                    done = t.done,
                    doneAt = t.doneAt,
                    dueDate = t.dueDate,
                    startDate = t.startDate,
                    endDate = t.endDate,
                    priority = t.priority,
                    repeatAfter = t.repeatAfter,
                    repeatMode = t.repeatMode,
                    percentDone = t.percentDone,
                    hexColor = t.hexColor,
                    isFavorite = t.isFavorite,
                    bucketId = t.bucketId,
                    position = t.position,
                    created = t.created,
                    updated = t.updated,
                    createdById = t.createdBy?.id ?: 0L,
                    createdByUsername = t.createdBy?.username ?: "",
                    assigneeIds = t.assignees.joinToString(",") { it.id.toString() },
                    assigneeUsernames = t.assignees.joinToString(",") { it.username }
                )
            )
            val dueMillis = DateUtils.parseIsoToMillis(t.dueDate)
            if (dueMillis != null && dueMillis > System.currentTimeMillis() && !t.done) {
                alarmScheduler.scheduleExactAlarm(t.id, t.title, dueMillis, isDueDate = true)
            } else {
                alarmScheduler.cancelAlarm(t.id, isDueDate = true)
            }
            Result.success(t)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: Long): Result<Unit> {
        return try {
            alarmScheduler.cancelAlarm(taskId, isDueDate = true)
            database.taskDao().deleteTask(taskId)
            networkClient.getApiService().deleteTask(taskId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun duplicateTask(taskId: Long): Result<Task> {
        return try {
            val t = networkClient.getApiService().duplicateTask(taskId)
            Result.success(t)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class ViewRepository(private val networkClient: NetworkClient) {
    suspend fun getViews(projectId: Long): Result<List<ProjectView>> {
        return try {
            val views = networkClient.getApiService().getProjectViews(projectId)
            Result.success(views)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBuckets(projectId: Long, viewId: Long): Result<List<Bucket>> {
        return try {
            val buckets = networkClient.getApiService().getBuckets(projectId, viewId)
            Result.success(buckets)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createBucket(projectId: Long, viewId: Long, title: String, limit: Long = 0): Result<Bucket> {
        return try {
            val bucket = networkClient.getApiService().createBucket(projectId, viewId, BucketCreateRequest(title, limit))
            Result.success(bucket)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBucket(projectId: Long, viewId: Long, bucketId: Long): Result<Unit> {
        return try {
            networkClient.getApiService().deleteBucket(projectId, viewId, bucketId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun moveTaskToBucket(projectId: Long, viewId: Long, bucketId: Long, taskId: Long): Result<Unit> {
        return try {
            networkClient.getApiService().moveTaskToBucket(projectId, viewId, bucketId, MoveTaskToBucketRequest(taskId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class SyncRepository(
    private val projectRepository: ProjectRepository,
    private val taskRepository: TaskRepository,
    private val networkClient: NetworkClient,
    private val database: VikunjaDatabase,
    private val notificationHelper: NotificationHelper
) {
    suspend fun syncAll(): Result<Unit> {
        return try {
            projectRepository.refreshProjects()
            taskRepository.refreshTasks()

            // Poll server notifications
            val api = networkClient.getApiService()
            val notifications = api.getNotifications(perPage = 20)
            val unread = notifications.filter { !it.read }
            for (n in unread) {
                notificationHelper.showServerNotification(n.id, n.name, n.subject)
            }
            database.notificationDao().insertNotifications(
                notifications.map {
                    NotificationEntity(it.id, it.name, it.subject, it.read, it.created)
                }
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
