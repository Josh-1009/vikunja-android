package cloud.codella.vikunja.data.mapper

import cloud.codella.vikunja.data.local.entity.LabelEntity
import cloud.codella.vikunja.data.local.entity.TaskEntity
import cloud.codella.vikunja.data.remote.dto.RemoteLabelDto
import cloud.codella.vikunja.data.remote.dto.RemoteTaskDto
import cloud.codella.vikunja.data.remote.dto.RemoteTaskMutationRequest
import cloud.codella.vikunja.domain.model.Label
import cloud.codella.vikunja.domain.model.Task

fun TaskEntity.toDomain(labels: List<Label> = emptyList()): Task = Task(
    id = id,
    title = title,
    description = description,
    projectId = projectId,
    done = done,
    doneAt = doneAt,
    dueDate = dueDate,
    startDate = startDate,
    endDate = endDate,
    priority = priority,
    repeatAfter = repeatAfter,
    repeatMode = repeatMode,
    percentDone = percentDone,
    hexColor = hexColor,
    isFavorite = isFavorite,
    bucketId = bucketId,
    position = position,
    created = created,
    updated = updated,
    createdById = createdById,
    createdByUsername = createdByUsername,
    labels = labels,
    assigneeUsernames = if (assigneeUsernames.isBlank()) emptyList() else assigneeUsernames.split(","),
    isMyDay = isMyDay,
    myDayDate = myDayDate
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    projectId = projectId,
    done = done,
    doneAt = doneAt,
    dueDate = dueDate,
    startDate = startDate,
    endDate = endDate,
    priority = priority,
    repeatAfter = repeatAfter,
    repeatMode = repeatMode,
    percentDone = percentDone,
    hexColor = hexColor,
    isFavorite = isFavorite,
    bucketId = bucketId,
    position = position,
    created = created,
    updated = updated,
    createdById = createdById,
    createdByUsername = createdByUsername,
    assigneeIds = "",
    assigneeUsernames = assigneeUsernames.joinToString(","),
    isMyDay = isMyDay,
    myDayDate = myDayDate
)

fun RemoteTaskDto.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    projectId = projectId,
    done = done,
    doneAt = doneAt,
    dueDate = dueDate,
    startDate = startDate,
    endDate = endDate,
    priority = priority,
    repeatAfter = repeatAfter,
    repeatMode = repeatMode,
    percentDone = percentDone,
    hexColor = hexColor,
    isFavorite = isFavorite,
    bucketId = bucketId,
    position = position,
    created = created,
    updated = updated,
    createdById = 0L,
    createdByUsername = "",
    assigneeIds = assignees.joinToString(",") { it.id.toString() },
    assigneeUsernames = assignees.joinToString(",") { it.username }
)

fun RemoteTaskDto.toDomain(): Task = Task(
    id = id,
    title = title,
    description = description,
    projectId = projectId,
    done = done,
    doneAt = doneAt,
    dueDate = dueDate,
    startDate = startDate,
    endDate = endDate,
    priority = priority,
    repeatAfter = repeatAfter,
    repeatMode = repeatMode,
    percentDone = percentDone,
    hexColor = hexColor,
    isFavorite = isFavorite,
    bucketId = bucketId,
    position = position,
    created = created,
    updated = updated,
    createdById = 0L,
    createdByUsername = "",
    labels = labels.map { it.toDomain() },
    assigneeUsernames = assignees.map { it.username }
)

fun Task.toMutationRequest(): RemoteTaskMutationRequest = RemoteTaskMutationRequest(
    title = title,
    description = description,
    done = done,
    projectId = projectId,
    dueDate = dueDate,
    startDate = startDate,
    endDate = endDate,
    priority = priority,
    repeatAfter = repeatAfter,
    repeatMode = repeatMode,
    percentDone = percentDone,
    hexColor = hexColor,
    isFavorite = isFavorite
)

fun RemoteLabelDto.toDomain(): Label = Label(
    id = id,
    title = title,
    description = description,
    hexColor = hexColor
)

fun LabelEntity.toDomain(): Label = Label(
    id = id,
    title = title,
    description = description,
    hexColor = hexColor
)

fun Label.toEntity(): LabelEntity = LabelEntity(
    id = id,
    title = title,
    description = description,
    hexColor = hexColor
)
