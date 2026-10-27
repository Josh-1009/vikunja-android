package cloud.codella.vikunja.data.repository

import cloud.codella.vikunja.data.local.dao.ProjectDao
import cloud.codella.vikunja.data.local.dao.SyncQueueDao
import cloud.codella.vikunja.data.local.entity.SyncQueueEntity
import cloud.codella.vikunja.data.mapper.toDomain
import cloud.codella.vikunja.data.mapper.toEntity
import cloud.codella.vikunja.data.mapper.toMutationRequest
import cloud.codella.vikunja.domain.model.Project
import cloud.codella.vikunja.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProjectRepositoryImpl(
    private val projectDao: ProjectDao,
    private val syncQueueDao: SyncQueueDao,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) : ProjectRepository {

    override fun getProjectsFlow(): Flow<List<Project>> {
        return projectDao.getAllProjectsFlow().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getProjectById(id: Long): Project? {
        return projectDao.getProjectById(id)?.toDomain()
    }

    override suspend fun createProject(project: Project): Long {
        val negativeId = -System.currentTimeMillis()
        val localProject = project.copy(
            id = negativeId,
            updated = java.time.Instant.now().toString()
        )
        projectDao.insertProject(localProject.toEntity())

        val payload = json.encodeToString(localProject.toMutationRequest())
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "PROJECT",
                entityId = negativeId,
                operation = "CREATE",
                payloadJson = payload,
                createdAt = System.currentTimeMillis()
            )
        )
        return negativeId
    }

    override suspend fun updateProject(project: Project) {
        val updatedProject = project.copy(updated = java.time.Instant.now().toString())
        projectDao.updateProject(updatedProject.toEntity())

        val payload = json.encodeToString(updatedProject.toMutationRequest())
        syncQueueDao.enqueue(
            SyncQueueEntity(
                entityType = "PROJECT",
                entityId = project.id,
                operation = "UPDATE",
                payloadJson = payload,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteProject(id: Long) {
        projectDao.deleteProject(id)
        if (id < 0) {
            syncQueueDao.deleteByEntity("PROJECT", id)
        } else {
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    entityType = "PROJECT",
                    entityId = id,
                    operation = "DELETE",
                    payloadJson = "{}",
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }
}
