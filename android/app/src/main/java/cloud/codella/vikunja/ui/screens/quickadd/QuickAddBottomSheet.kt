package cloud.codella.vikunja.ui.screens.quickadd

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.codella.vikunja.R
import cloud.codella.vikunja.core.database.entity.ProjectEntity
import cloud.codella.vikunja.core.util.AdvancedNlpParser
import cloud.codella.vikunja.core.util.DateUtils
import cloud.codella.vikunja.data.repository.ProjectRepository
import cloud.codella.vikunja.data.repository.TaskRepository
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun QuickAddBottomSheet(
    taskRepository: TaskRepository,
    projectRepository: ProjectRepository,
    onDismiss: () -> Unit
) {
    var rawInput by remember { mutableStateOf("") }
    var selectedProjectId by remember { mutableStateOf<Long?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    val projects by projectRepository.projectsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Speech-to-Text Launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                rawInput = if (rawInput.isBlank()) spokenText else "$rawInput $spokenText"
            }
        }
    }

    LaunchedEffect(projects) {
        if (selectedProjectId == null && projects.isNotEmpty()) {
            selectedProjectId = projects.first().id
        }
    }

    // Live NLP preview
    val parsedNlp = remember(rawInput) {
        if (rawInput.isNotBlank()) AdvancedNlpParser.parse(rawInput) else null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.quick_add),
                    style = MaterialTheme.typography.titleMedium
                )

                // Voice Dictation Button
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_speak_prompt))
                            }
                            voiceLauncher.launch(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = stringResource(R.string.voice_capture_btn),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = stringResource(R.string.quick_add_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = rawInput,
                onValueChange = { rawInput = it },
                placeholder = { Text("e.g. Call dentist tomorrow at 3pm !urgent #health") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
                trailingIcon = {
                    if (rawInput.isNotBlank()) {
                        IconButton(onClick = { rawInput = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )

            // Live NLP Detection Chips (Todoist-style visual feedback)
            if (parsedNlp != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (parsedNlp.dueDateIso != null) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    "📅 ${DateUtils.formatDisplayDateTime(parsedNlp.dueDateIso)}",
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }

                    if (parsedNlp.priority != null && parsedNlp.priority > 0) {
                        val pText = when (parsedNlp.priority) {
                            5 -> "🔴 Urgent"
                            4 -> "🟠 High"
                            3 -> "🟡 Med"
                            else -> "🔵 Low"
                        }
                        SuggestionChip(
                            onClick = {},
                            label = { Text(pText, fontSize = 11.sp) }
                        )
                    }

                    if (parsedNlp.projectQuery != null) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("📁 ~${parsedNlp.projectQuery}", fontSize = 11.sp) }
                        )
                    }

                    parsedNlp.labels.forEach { label ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text("🏷️ #$label", fontSize = 11.sp) }
                        )
                    }
                }
            }

            // Project Selector Dropdown
            var dropdownExpanded by remember { mutableStateOf(false) }
            val currentProject = projects.firstOrNull { it.id == selectedProjectId }

            Box {
                OutlinedButton(
                    onClick = { dropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(currentProject?.title ?: "Select Project")
                }
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    projects.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.title) },
                            onClick = {
                                selectedProjectId = p.id
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (rawInput.isNotBlank() && selectedProjectId != null) {
                        isSubmitting = true
                        scope.launch {
                            taskRepository.quickAddTask(rawInput.trim(), selectedProjectId!!)
                            isSubmitting = false
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = rawInput.isNotBlank() && !isSubmitting
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.create_task))
            }
        }
    }
}
