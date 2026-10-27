package cloud.codella.vikunja.presentation.tasks.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cloud.codella.vikunja.presentation.util.MagicSyntaxParser
import cloud.codella.vikunja.presentation.util.ParsedTaskDraft

@Composable
fun QuickTaskInput(
    onTaskSubmit: (cleanTitle: String, dueDateIso: String?, priority: Int, labels: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false
) {
    var rawText by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(autoFocus) }
    val focusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            isExpanded = true
            focusRequester.requestFocus()
        }
    }

    val parsedDraft: ParsedTaskDraft by remember(rawText) {
        derivedStateOf { MagicSyntaxParser.parse(rawText) }
    }

    val canSubmit = parsedDraft.cleanTitle.isNotBlank()
    val submitScale by animateFloatAsState(
        targetValue = if (canSubmit) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "submit_scale"
    )

    fun submit() {
        if (!canSubmit) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onTaskSubmit(
            parsedDraft.cleanTitle,
            parsedDraft.dueDateIso,
            parsedDraft.priority,
            parsedDraft.labels
        )
        rawText = ""
        isExpanded = false
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = rawText,
                    onValueChange = {
                        rawText = it
                        if (!isExpanded && it.isNotEmpty()) isExpanded = true
                    },
                    placeholder = {
                        Text(
                            text = "Add task... e.g. Fix bug *tomorrow !4 #dev",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = false,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() })
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = { submit() },
                    enabled = canSubmit,
                    modifier = Modifier
                        .size(48.dp)
                        .scale(submitScale)
                        .clip(CircleShape)
                        .background(
                            if (canSubmit) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .semantics {
                            role = Role.Button
                            contentDescription = "Submit task"
                        }
                ) {
                    Icon(
                        imageVector = if (canSubmit) Icons.Default.ArrowUpward else Icons.Default.Add,
                        contentDescription = null,
                        tint = if (canSubmit) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            // Real-time parsed syntax tokens
            AnimatedVisibility(
                visible = parsedDraft.rawTokens.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 4.dp, end = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Due Date Chip
                    parsedDraft.dueDateLabel?.let { label ->
                        AssistChip(
                            onClick = { },
                            label = { Text(label, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Priority Chip
                    if (parsedDraft.priority > 0) {
                        val (priorityLabel, priorityColor) = when (parsedDraft.priority) {
                            5 -> "Urgent (5)" to Color(0xFFE11D48)
                            4 -> "High (4)" to Color(0xFFEA580C)
                            3 -> "Medium (3)" to Color(0xFF2563EB)
                            2 -> "Low (2)" to Color(0xFF64748B)
                            else -> "None" to Color.Gray
                        }
                        AssistChip(
                            onClick = { },
                            label = { Text(priorityLabel, fontWeight = FontWeight.SemiBold) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = priorityColor
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = priorityColor.copy(alpha = 0.15f),
                                labelColor = priorityColor
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Labels
                    parsedDraft.labels.forEach { labelName ->
                        AssistChip(
                            onClick = { },
                            label = { Text("#$labelName") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Label,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Project
                    parsedDraft.projectName?.let { proj ->
                        AssistChip(
                            onClick = { },
                            label = { Text("~$proj") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }
        }
    }
}
