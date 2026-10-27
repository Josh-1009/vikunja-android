package cloud.codella.vikunja.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import cloud.codella.vikunja.MainActivity
import cloud.codella.vikunja.VikunjaApp
import cloud.codella.vikunja.core.database.entity.TaskEntity
import cloud.codella.vikunja.core.model.TaskCreateUpdateRequest
import cloud.codella.vikunja.core.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VikunjaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTasksWidget()
}

val taskIdParamKey = ActionParameters.Key<Long>("taskId")
val taskTitleParamKey = ActionParameters.Key<String>("taskTitle")

class CompleteTaskActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[taskIdParamKey] ?: return
        val taskTitle = parameters[taskTitleParamKey] ?: "Task"
        val app = context.applicationContext as? VikunjaApp ?: return

        withContext(Dispatchers.IO) {
            try {
                app.database.taskDao().updateTaskDone(taskId, true)
                app.apiService.updateTask(taskId, TaskCreateUpdateRequest(title = taskTitle, done = true))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        TodayTasksWidget().update(context, glanceId)
    }
}

class RefreshWidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val app = context.applicationContext as? VikunjaApp
        withContext(Dispatchers.IO) {
            try {
                app?.syncRepository?.syncAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        TodayTasksWidget().update(context, glanceId)
    }
}

class TodayTasksWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as? VikunjaApp
        val tasks = withContext(Dispatchers.IO) {
            val all = app?.database?.taskDao()?.getTasksWithDueDate() ?: emptyList()
            all.filter {
                val due = DateUtils.parseIsoToMillis(it.dueDate)
                due != null && (DateUtils.isToday(due) || DateUtils.isOverdue(due))
            }.sortedWith(
                compareByDescending<TaskEntity> { it.priority }
                    .thenBy { it.dueDate ?: "" }
            )
        }

        provideContent {
            WidgetContent(context, tasks)
        }
    }

    @Composable
    private fun WidgetContent(context: Context, tasks: List<TaskEntity>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(12.dp)
        ) {
            // Header: Title + Task Count + Quick Add button + Refresh
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(actionStartActivity<MainActivity>())
                ) {
                    Text(
                        text = "Vikunja Agenda",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = GlanceTheme.colors.onSurface
                        )
                    )
                    Text(
                        text = "${tasks.size} tasks for today",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }

                // Refresh Button
                Box(
                    modifier = GlanceModifier
                        .padding(horizontal = 4.dp)
                        .cornerRadius(8.dp)
                        .background(GlanceTheme.colors.surfaceVariant)
                        .clickable(actionRunCallback<RefreshWidgetActionCallback>())
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "↻",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }

                // Quick Add Button (+)
                val quickAddIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("vikunja://quick_add"),
                    context,
                    MainActivity::class.java
                )
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(8.dp)
                        .background(GlanceTheme.colors.primary)
                        .clickable(actionStartActivity(quickAddIntent))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "+ Add",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.onPrimary
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (tasks.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All tasks done for today! 🎉",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(tasks) { task ->
                        TaskRow(context, task)
                    }
                }
            }
        }
    }

    @Composable
    private fun TaskRow(context: Context, task: TaskEntity) {
        val openTaskIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("vikunja://task/${task.id}"),
            context,
            MainActivity::class.java
        )

        val priorityColor = when (task.priority) {
            5 -> Color(0xFFE53935) // Red
            4 -> Color(0xFFFF9800) // Orange
            3 -> Color(0xFFFFB300) // Amber
            2 -> Color(0xFF1E88E5) // Blue
            else -> Color(0xFF9E9E9E) // Gray
        }

        val dueMillis = DateUtils.parseIsoToMillis(task.dueDate)
        val isOverdue = dueMillis != null && DateUtils.isOverdue(dueMillis)
        val timeStr = dueMillis?.let {
            java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(it))
        } ?: ""

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .cornerRadius(8.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Interactive Complete Checkbox button
            Box(
                modifier = GlanceModifier
                    .size(24.dp)
                    .cornerRadius(6.dp)
                    .background(GlanceTheme.colors.surface)
                    .clickable(
                        actionRunCallback<CompleteTaskActionCallback>(
                            actionParametersOf(
                                taskIdParamKey to task.id,
                                taskTitleParamKey to task.title
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "○",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = GlanceTheme.colors.primary
                    )
                )
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Priority Indicator Strip
            Box(
                modifier = GlanceModifier
                    .width(4.dp)
                    .height(20.dp)
                    .cornerRadius(2.dp)
                    .background(priorityColor)
            ) {}

            Spacer(modifier = GlanceModifier.width(8.dp))

            // Task Title and Time
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(openTaskIntent))
            ) {
                Text(
                    text = task.title,
                    maxLines = 1,
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = GlanceTheme.colors.onSurface
                    )
                )
                if (timeStr.isNotEmpty()) {
                    Text(
                        text = if (isOverdue) "Overdue • $timeStr" else timeStr,
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = if (isOverdue) ColorProvider(Color(0xFFE53935)) else GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}
