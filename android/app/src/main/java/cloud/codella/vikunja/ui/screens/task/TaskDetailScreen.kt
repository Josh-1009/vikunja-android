package cloud.codella.vikunja.ui.screens.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import cloud.codella.vikunja.R
import cloud.codella.vikunja.core.model.Attachment
import cloud.codella.vikunja.core.model.Task
import cloud.codella.vikunja.core.model.TaskComment
import cloud.codella.vikunja.core.model.TaskCreateUpdateRequest
import cloud.codella.vikunja.core.network.VikunjaApiService
import cloud.codella.vikunja.core.util.DescriptionUtils
import cloud.codella.vikunja.core.util.GoogleCalendarHelper
import cloud.codella.vikunja.data.repository.TaskRepository
import cloud.codella.vikunja.presentation.tasks.attachments.AttachmentItemView
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

@Composable
fun TaskDetailScreen(
    taskId: Long,
    taskRepository: TaskRepository,
    apiService: VikunjaApiService,
    taskRelationRepository: cloud.codella.vikunja.domain.repository.TaskRelationRepository? = null,
    onNavigateBack: () -> Unit
) {
    var task by remember { mutableStateOf<Task?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    var priority by remember { mutableStateOf(0) }
    var percentDone by remember { mutableStateOf(0f) }
    var comments by remember { mutableStateOf<List<TaskComment>>(emptyList()) }
    var attachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var newCommentText by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val relationsFlow = remember(taskId) {
        taskRelationRepository?.getRelationsForTaskFlow(taskId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val relations by relationsFlow.collectAsState(initial = emptyList())
    val allDbTasks by taskRepository.allTasksFlow.collectAsState(initial = emptyList())
    val candidateTasks = remember(allDbTasks, taskId) { allDbTasks.filter { it.id != taskId } }
    var showAddRelationDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var isUploadingAttachment by remember { mutableStateOf(false) }

    fun uploadUri(uri: Uri) {
        scope.launch {
            isUploadingAttachment = true
            try {
                var fileName = "attachment"
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: fileName
                    }
                }

                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() } ?: throw IllegalStateException("Could not read file")
                val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("files", fileName, requestBody)

                apiService.uploadTaskAttachment(taskId, part)
                attachments = apiService.getTaskAttachments(taskId)
                Toast.makeText(context, "Attachment uploaded!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isUploadingAttachment = false
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { uploadUri(it) }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadUri(it) }
    }

    fun openAttachment(attachment: Attachment) {
        scope.launch {
            try {
                Toast.makeText(context, "Opening ${attachment.name}...", Toast.LENGTH_SHORT).show()
                val responseBody = apiService.downloadAttachment(taskId, attachment.id)
                val attachmentsDir = File(context.cacheDir, "attachments").apply { mkdirs() }
                val localFile = File(attachmentsDir, attachment.name)

                responseBody.byteStream().use { input ->
                    FileOutputStream(localFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    localFile
                )

                val extension = attachment.name.substringAfterLast('.', "").lowercase()
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(viewIntent, "Open with"))
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteAttachment(attachment: Attachment) {
        scope.launch {
            try {
                apiService.deleteTaskAttachment(taskId, attachment.id)
                attachments = attachments.filter { it.id != attachment.id }
                Toast.makeText(context, "Attachment removed", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(taskId) {
        val local = taskRepository.getLocalTaskById(taskId)
        if (local != null) {
            title = local.title
            description = DescriptionUtils.stripHtml(local.description)
            done = local.done
            priority = local.priority
            percentDone = (local.percentDone / 100.0).toFloat().coerceIn(0f, 1f)
            task = Task(
                id = local.id,
                title = local.title,
                description = local.description,
                projectId = local.projectId,
                done = local.done,
                dueDate = local.dueDate,
                startDate = local.startDate,
                endDate = local.endDate,
                priority = local.priority,
                percentDone = local.percentDone,
                isFavorite = local.isFavorite
            )
        }
        val result = taskRepository.getTaskDetails(taskId)
        if (result.isSuccess) {
            val t = result.getOrNull()!!
            task = t
            title = t.title
            description = DescriptionUtils.stripHtml(t.description)
            done = t.done
            priority = t.priority
            percentDone = (t.percentDone / 100.0).toFloat().coerceIn(0f, 1f)
        }
        try {
            comments = apiService.getTaskComments(taskId)
            attachments = apiService.getTaskAttachments(taskId)
        } catch (_: Exception) {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tasks)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    val activeFocusId by cloud.codella.vikunja.service.FocusTaskNotificationService.activeFocusTaskId.collectAsState()
                    val isFocusingThisTask = activeFocusId == taskId

                    IconButton(
                        onClick = {
                            if (isFocusingThisTask) {
                                cloud.codella.vikunja.service.FocusTaskNotificationService.stop(context)
                                Toast.makeText(context, "Focus mode stopped", Toast.LENGTH_SHORT).show()
                            } else {
                                cloud.codella.vikunja.service.FocusTaskNotificationService.start(
                                    context,
                                    taskId,
                                    title.ifBlank { "Task #$taskId" }
                                )
                                Toast.makeText(context, "🎯 Sticky Focus started in status bar", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isFocusingThisTask) Icons.Default.TimerOff else Icons.Default.Timer,
                            contentDescription = "Toggle Sticky Focus",
                            tint = if (isFocusingThisTask) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Google Calendar Export Button
                    IconButton(
                        onClick = {
                            GoogleCalendarHelper.exportTaskToCalendar(
                                context = context,
                                title = title.ifBlank { "Vikunja Task" },
                                description = description,
                                dueDateIso = task?.dueDate
                            )
                        }
                    ) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = stringResource(R.string.gcal_add_to_calendar),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Quick test reminder trigger button
                    IconButton(
                        onClick = {
                            val helper = cloud.codella.vikunja.core.notifications.NotificationHelper(context)
                            helper.showTaskNotification(taskId, title.ifBlank { "Test Task" }, "This task is due right now!", true)
                        }
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = "Test Reminder", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                taskRepository.duplicateTask(taskId)
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.duplicate))
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                taskRepository.deleteTask(taskId)
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        if (task == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Done toggle & Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = done,
                        onCheckedChange = {
                            done = it
                            scope.launch { taskRepository.toggleTaskDone(taskId, !it) }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.task_title)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Pomodoro Focus Timer Card
                val focusState by cloud.codella.vikunja.core.focus.FocusTimerService.timerState.collectAsState()
                val isThisTaskFocusing = focusState.isRunning && focusState.taskId == taskId

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isThisTaskFocusing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🍅", style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.focus_timer),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    if (isThisTaskFocusing) {
                                        val min = focusState.secondsRemaining / 60
                                        val sec = focusState.secondsRemaining % 60
                                        Text(
                                            text = "${if (focusState.phase == cloud.codella.vikunja.core.focus.FocusPhase.FOCUS) "Focus" else "Break"}: %02d:%02d".format(min, sec),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                        )
                                    } else {
                                        Text(
                                            text = "25m focus • 5m break",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (!isThisTaskFocusing) {
                                Button(
                                    onClick = {
                                        cloud.codella.vikunja.core.focus.FocusTimerService.start(
                                            context,
                                            taskId,
                                            title.ifBlank { "Task" }
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(stringResource(R.string.focus_start))
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (focusState.isPaused) {
                                        FilledTonalButton(
                                            onClick = { cloud.codella.vikunja.core.focus.FocusTimerService.resume(context) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(stringResource(R.string.focus_resume))
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { cloud.codella.vikunja.core.focus.FocusTimerService.pause(context) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(stringResource(R.string.focus_pause))
                                        }
                                    }
                                    IconButton(onClick = { cloud.codella.vikunja.core.focus.FocusTimerService.stop(context) }) {
                                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.task_description)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Priority Selector
                Text(stringResource(R.string.priority), style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0 to "None", 1 to "Low", 3 to "Med", 5 to "Urgent").forEach { (p, label) ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(label) }
                        )
                    }
                }

                // Progress Slider
                Text("${stringResource(R.string.percent_done)}: ${(percentDone * 100).toInt()}%")
                Slider(
                    value = percentDone,
                    onValueChange = { percentDone = it }
                )

                // Save Changes Button
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            taskRepository.updateTask(
                                taskId,
                                TaskCreateUpdateRequest(
                                    title = title.trim(),
                                    description = description.trim(),
                                    done = done,
                                    priority = priority,
                                    percentDone = (percentDone * 100).toDouble()
                                )
                            )
                            isSaving = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving
                ) {
                    Text(stringResource(R.string.save))
                }

                HorizontalDivider()

                // Comments Section
                Text(stringResource(R.string.comments), style = MaterialTheme.typography.titleMedium)
                for (comment in comments) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(comment.comment, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "By ${comment.author?.username ?: "User"} • ${comment.created.take(10)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCommentText,
                        onValueChange = { newCommentText = it },
                        placeholder = { Text("Add comment...") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newCommentText.isNotBlank()) {
                                scope.launch {
                                    apiService.addTaskComment(
                                        taskId,
                                        cloud.codella.vikunja.core.model.TaskCommentRequest(newCommentText.trim())
                                    )
                                    comments = apiService.getTaskComments(taskId)
                                    newCommentText = ""
                                }
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }

                HorizontalDivider()

                // Attachments Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${stringResource(R.string.attachments)} (${attachments.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            enabled = !isUploadingAttachment
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add Photo", tint = MaterialTheme.colorScheme.primary)
                        }

                        IconButton(
                            onClick = { documentPickerLauncher.launch("*/*") },
                            enabled = !isUploadingAttachment
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach File", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                if (isUploadingAttachment) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Uploading attachment...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (attachments.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Attachment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No attachments yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tap the photo or file icon above to attach",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (att in attachments) {
                            AttachmentItemView(
                                attachment = att,
                                onViewClick = { openAttachment(it) },
                                onDeleteClick = { deleteAttachment(it) }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Dependencies & Relations Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Dependencies & Relations (${relations.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )

                    IconButton(onClick = { showAddRelationDialog = true }) {
                        Icon(Icons.Default.AddLink, contentDescription = "Add Dependency", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (relations.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No dependencies linked",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tap the link icon above to connect related tasks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (rel in relations) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when (rel.relationKind) {
                                                cloud.codella.vikunja.domain.model.RelationKind.BLOCKED_BY -> MaterialTheme.colorScheme.errorContainer
                                                cloud.codella.vikunja.domain.model.RelationKind.BLOCKS -> MaterialTheme.colorScheme.tertiaryContainer
                                                else -> MaterialTheme.colorScheme.secondaryContainer
                                            }
                                        ) {
                                            Text(
                                                text = rel.relationKind.displayName,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                color = when (rel.relationKind) {
                                                    cloud.codella.vikunja.domain.model.RelationKind.BLOCKED_BY -> MaterialTheme.colorScheme.onErrorContainer
                                                    cloud.codella.vikunja.domain.model.RelationKind.BLOCKS -> MaterialTheme.colorScheme.onTertiaryContainer
                                                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = rel.targetTaskTitle.ifBlank { "Task #${rel.targetTaskId}" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                taskRelationRepository?.deleteRelation(taskId, rel.targetTaskId, rel.relationKind)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove Relation",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddRelationDialog) {
        var selectedKind by remember { mutableStateOf(cloud.codella.vikunja.domain.model.RelationKind.BLOCKED_BY) }
        var selectedTargetTask by remember { mutableStateOf<cloud.codella.vikunja.core.database.entity.TaskEntity?>(null) }
        var searchQuery by remember { mutableStateOf("") }

        val filteredCandidates = remember(candidateTasks, searchQuery) {
            if (searchQuery.isBlank()) candidateTasks
            else candidateTasks.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showAddRelationDialog = false },
            title = { Text("Add Task Relation", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Relation Type:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val kinds = listOf(
                            cloud.codella.vikunja.domain.model.RelationKind.BLOCKED_BY,
                            cloud.codella.vikunja.domain.model.RelationKind.BLOCKS,
                            cloud.codella.vikunja.domain.model.RelationKind.CHILD_OF,
                            cloud.codella.vikunja.domain.model.RelationKind.DUPLICATES
                        )
                        kinds.forEach { kind ->
                            FilterChip(
                                selected = selectedKind == kind,
                                onClick = { selectedKind = kind },
                                label = { Text(kind.displayName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search tasks...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredCandidates.size) { index ->
                            val cTask = filteredCandidates[index]
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedTargetTask = cTask }
                                    .clip(RoundedCornerShape(8.dp)),
                                color = if (selectedTargetTask?.id == cTask.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            ) {
                                Text(
                                    text = cTask.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedTargetTask?.let { target ->
                            scope.launch {
                                taskRelationRepository?.addRelation(
                                    taskId = taskId,
                                    targetTaskId = target.id,
                                    relationKind = selectedKind,
                                    targetTaskTitle = target.title
                                )
                                showAddRelationDialog = false
                            }
                        }
                    },
                    enabled = selectedTargetTask != null
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRelationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
