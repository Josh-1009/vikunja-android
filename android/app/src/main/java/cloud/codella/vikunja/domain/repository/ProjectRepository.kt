package cloud.codella.vikunja.domain.repository

import cloud.codella.vikunja.domain.model.Project
import kotlinx.coroutines.flow.Flow

interface ProjectRepository {
    fun getProjectsFlow(): Flow<List<Project>>
    suspend fun getProjectById(id: Long): Project?
    suspend fun createProject(project: Project): Long
    suspend fun updateProject(project: Project)
    suspend fun deleteProject(id: Long)
}
