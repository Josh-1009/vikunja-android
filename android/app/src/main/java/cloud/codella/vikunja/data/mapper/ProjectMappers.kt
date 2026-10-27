package cloud.codella.vikunja.data.mapper

import cloud.codella.vikunja.data.local.entity.ProjectEntity
import cloud.codella.vikunja.data.remote.dto.RemoteProjectDto
import cloud.codella.vikunja.data.remote.dto.RemoteProjectMutationRequest
import cloud.codella.vikunja.domain.model.Project

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    title = title,
    description = description,
    hexColor = hexColor,
    ownerId = ownerId,
    parentProjectId = parentProjectId,
    isArchived = isArchived,
    isFavorite = isFavorite,
    position = position,
    updated = updated
)

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    title = title,
    description = description,
    hexColor = hexColor,
    ownerId = ownerId,
    parentProjectId = parentProjectId,
    isArchived = isArchived,
    isFavorite = isFavorite,
    position = position,
    updated = updated
)

fun RemoteProjectDto.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    title = title,
    description = description ?: "",
    hexColor = hexColor ?: "",
    ownerId = ownerId,
    parentProjectId = if (parentProjectId == null || parentProjectId == 0L) null else parentProjectId,
    isArchived = isArchived,
    isFavorite = isFavorite,
    position = position ?: 0.0,
    updated = updated ?: ""
)

fun Project.toMutationRequest(): RemoteProjectMutationRequest = RemoteProjectMutationRequest(
    title = title,
    description = description,
    hexColor = hexColor,
    parentProjectId = parentProjectId,
    isArchived = isArchived,
    isFavorite = isFavorite
)
