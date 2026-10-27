package cloud.codella.vikunja.presentation.tasks.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.codella.vikunja.domain.model.Task
import cloud.codella.vikunja.ui.theme.getPriorityColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListItem(
    task: Task,
    onToggleDone: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swiped Right -> Toggle Done
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleDone(!task.done)
                    false // Return false so item springs back if toggled, or let LazyColumn animate
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swiped Left -> Delete
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF16A34A) // Green for Complete
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFDC2626) // Red for Delete
                    SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surfaceVariant
                },
                label = "swipe_bg_color"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                }
            ) {
                if (direction == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Mark task done",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete task",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        content = {
            TaskCardContent(
                task = task,
                onToggleDone = onToggleDone,
                onClick = onClick
            )
        }
    )
}

@Composable
private fun TaskCardContent(
    task: Task,
    onToggleDone: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val checkmarkScale by animateFloatAsState(
        targetValue = if (task.done) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "checkbox_bounce"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = buildString {
                    append(if (task.done) "Completed task: " else "Task: ")
                    append(task.title)
                    if (task.dueDate != null) append(", due ${formatDate(task.dueDate)}")
                    if (task.priority > 0) append(", priority ${task.priority}")
                }
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox with minimum 48x48dp hit area
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleDone(!task.done)
                    }
                    .semantics {
                        role = Role.Checkbox
                        stateDescription = if (task.done) "Completed" else "Not completed"
                    },
                contentAlignment = Alignment.Center
            ) {
                Checkbox(
                    checked = task.done,
                    onCheckedChange = null,
                    modifier = Modifier.scale(checkmarkScale),
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF16A34A),
                        checkmarkColor = Color.White
                    )
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (task.done) FontWeight.Normal else FontWeight.Medium,
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (task.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (task.dueDate != null || task.priority > 0 || task.labels.isNotEmpty() || (task.isBlocked && !task.done)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Blocked Badge
                        if (task.isBlocked && !task.done) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Blocked",
                                        modifier = Modifier.size(11.dp),
                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Blocked",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        // Due date indicator
                        task.dueDate?.let { due ->
                            val formatted = formatDate(due)
                            Text(
                                text = formatted,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOverdue(due) && !task.done) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (isOverdue(due)) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        // Priority Flag
                        if (task.priority > 0) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = "Priority ${task.priority}",
                                modifier = Modifier.size(14.dp),
                                tint = getPriorityColor(task.priority)
                            )
                        }

                        // Labels preview
                        task.labels.take(2).forEach { label ->
                            Text(
                                text = "#${label.title}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDate(isoString: String): String {
    return try {
        val date = Instant.parse(isoString).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        when {
            date.isEqual(today) -> "Today"
            date.isEqual(today.plusDays(1)) -> "Tomorrow"
            date.isEqual(today.minusDays(1)) -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("MMM d"))
        }
    } catch (_: Exception) {
        isoString.substringBefore("T")
    }
}

private fun isOverdue(isoString: String): Boolean {
    return try {
        val date = Instant.parse(isoString).atZone(ZoneId.systemDefault()).toLocalDate()
        date.isBefore(LocalDate.now())
    } catch (_: Exception) {
        false
    }
}
