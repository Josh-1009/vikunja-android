package cloud.codella.vikunja.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cloud.codella.vikunja.domain.model.Project
import cloud.codella.vikunja.presentation.tasks.SmartViewType
import cloud.codella.vikunja.presentation.tasks.ViewMode
import cloud.codella.vikunja.ui.theme.parseProjectColor

@Composable
fun ProjectDrawerContent(
    projects: List<Project>,
    selectedSmartView: SmartViewType,
    selectedProjectId: Long?,
    currentViewMode: ViewMode,
    onSelectSmartView: (SmartViewType) -> Unit,
    onSelectProject: (projectId: Long, projectTitle: String) -> Unit,
    onSwitchViewMode: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier.width(320.dp),
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Vikunja",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Self-Hosted Tasks",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // View Switcher (List vs Kanban)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ViewModeButton(
                        title = "List View",
                        icon = Icons.Default.FormatListBulleted,
                        isSelected = currentViewMode == ViewMode.LIST,
                        onClick = { onSwitchViewMode(ViewMode.LIST) },
                        modifier = Modifier.weight(1f)
                    )
                    ViewModeButton(
                        title = "Kanban",
                        icon = Icons.Default.ViewKanban,
                        isSelected = currentViewMode == ViewMode.KANBAN,
                        onClick = { onSwitchViewMode(ViewMode.KANBAN) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Smart Views
            Text(
                text = "OVERVIEW",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )

            SmartViewItem(
                title = "Inbox",
                icon = Icons.Default.Inbox,
                isSelected = selectedProjectId == null && selectedSmartView == SmartViewType.INBOX,
                onClick = { onSelectSmartView(SmartViewType.INBOX) }
            )
            SmartViewItem(
                title = "Today",
                icon = Icons.Default.Today,
                isSelected = selectedProjectId == null && selectedSmartView == SmartViewType.TODAY,
                onClick = { onSelectSmartView(SmartViewType.TODAY) }
            )
            SmartViewItem(
                title = "Upcoming",
                icon = Icons.Default.CalendarMonth,
                isSelected = selectedProjectId == null && selectedSmartView == SmartViewType.UPCOMING,
                onClick = { onSelectSmartView(SmartViewType.UPCOMING) }
            )
            SmartViewItem(
                title = "Completed",
                icon = Icons.Default.CheckCircle,
                isSelected = selectedProjectId == null && selectedSmartView == SmartViewType.COMPLETED,
                onClick = { onSelectSmartView(SmartViewType.COMPLETED) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Projects List
            Text(
                text = "PROJECTS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )

            if (projects.isEmpty()) {
                Text(
                    text = "No projects found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            } else {
                projects.forEach { project ->
                    val isSelected = selectedProjectId == project.id
                    val projectColor = parseProjectColor(project.hexColor)

                    NavigationDrawerItem(
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(projectColor)
                                )
                                Text(
                                    text = project.title,
                                    maxLines = 1,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        },
                        selected = isSelected,
                        onClick = { onSelectProject(project.id, project.title) },
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewModeButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
        tonalElevation = if (isSelected) 2.dp else 0.dp,
        modifier = modifier
            .height(36.dp)
            .semantics {
                role = Role.Button
                contentDescription = "$title, ${if (isSelected) "Selected" else "Not selected"}"
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SmartViewItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = { Icon(imageVector = icon, contentDescription = null) },
        label = { Text(title, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(12.dp)
    )
}
