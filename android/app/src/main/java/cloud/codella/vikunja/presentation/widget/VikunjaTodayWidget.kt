package cloud.codella.vikunja.presentation.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import cloud.codella.vikunja.core.database.VikunjaDatabase
import cloud.codella.vikunja.core.database.entity.TaskEntity
import cloud.codella.vikunja.core.util.DateUtils
import cloud.codella.vikunja.data.work.SyncWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VikunjaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VikunjaTodayWidget()
}

val widgetTaskIdKey = ActionParameters.Key<Long>("widget_task_id")
val openQuickAddActionKey = ActionParameters.Key<Boolean>(MainActivity.EXTRA_OPEN_QUICK_ADD)
val openTaskIdActionKey = ActionParameters.Key<Long>("extra_task_id")

class ToggleTaskActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val taskId = parameters[widgetTaskIdKey] ?: return
        Log.d("VikunjaWidget", "Toggling task #$taskId completion from widget")

        withContext(Dispatchers.IO) {
            val app = context.applicationContext as? VikunjaApp
            if (app != null) {
                app.cleanTaskRepository.toggleTaskDone(taskId, isDone = true)
            } else {
                val db = VikunjaDatabase.getInstance(context)
                db.taskDao().updateTaskDone(taskId, true)
            }
            // Trigger immediate background push to sync with self-hosted server
            SyncWorkScheduler.triggerExpeditedPush(context)
        }

        // Optimistic zero-latency update of the widget
        VikunjaTodayWidget().update(context, glanceId)
    }
}

class VikunjaTodayWidget : GlanceAppWidget() {

    companion object {
        private const val TAG = "VikunjaTodayWidget"

        private val SMALL_BOX = DpSize(100.dp, 100.dp)
        private val HORIZONTAL_RECTANGLE = DpSize(250.dp, 100.dp)
        private val BIG_RECTANGLE = DpSize(250.dp, 200.dp)

        suspend fun updateAll(context: Context) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(VikunjaTodayWidget::class.java)
                val widget = VikunjaTodayWidget()
                glanceIds.forEach { id ->
                    widget.update(context, id)
                }
                Log.d(TAG, "Updated ${glanceIds.size} widget instance(s)")
            } catch (e: Exception) {
                Log.w(TAG, "Could not update Glance widgets: ${e.message}")
            }
        }
    }

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SMALL_BOX, HORIZONTAL_RECTANGLE, BIG_RECTANGLE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val tasks = withContext(Dispatchers.IO) {
            val db = VikunjaDatabase.getInstance(context)
            val allTasks = db.taskDao().getTasksWithDueDate()
            allTasks.filter { task ->
                !task.done && isDueTodayOrOverdue(task.dueDate)
            }.sortedWith(
                compareByDescending<TaskEntity> { it.priority }
                    .thenBy { it.dueDate ?: "" }
            )
        }

        provideContent {
            GlanceTheme {
                WidgetRoot(context, tasks)
            }
        }
    }

    private fun isDueTodayOrOverdue(dueDateIso: String?): Boolean {
        if (dueDateIso.isNullOrBlank()) return false
        val millis = DateUtils.parseIsoToMillis(dueDateIso) ?: return false
        return DateUtils.isToday(millis) || millis < System.currentTimeMillis()
    }

    @Composable
    private fun WidgetRoot(context: Context, tasks: List<TaskEntity>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(12.dp)
        ) {
            // Header: Title + Badge + Quick Add Button
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
                        text = "Today's Tasks",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = GlanceTheme.colors.onSurface
                        )
                    )
                    Text(
                        text = if (tasks.isEmpty()) "0 pending" else "${tasks.size} remaining",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }

                // Quick Add "+" Button
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(12.dp)
                        .background(GlanceTheme.colors.primary)
                        .clickable(actionStartActivity<MainActivity>(actionParametersOf(openQuickAddActionKey to true)))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
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
                        text = "All caught up for today! 🎉",
                        style = TextStyle(
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(tasks) { task ->
                        TaskItemRow(context, task)
                    }
                }
            }
        }
    }

    @Composable
    private fun TaskItemRow(context: Context, task: TaskEntity) {
        val priorityColor = when (task.priority) {
            5 -> Color(0xFFE53935) // Urgent / Red
            4 -> Color(0xFFFF9800) // High / Orange
            3 -> Color(0xFFFFB300) // Medium / Amber
            2 -> Color(0xFF1E88E5) // Low / Blue
            else -> Color(0xFF9E9E9E) // None / Gray
        }

        val dueMillis = DateUtils.parseIsoToMillis(task.dueDate)
        val isOverdue = dueMillis != null && dueMillis < System.currentTimeMillis() && !DateUtils.isToday(dueMillis)
        val timeStr = dueMillis?.let {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it))
        } ?: ""

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .cornerRadius(10.dp)
                .background(GlanceTheme.colors.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Interactive Checkbox Box
            Box(
                modifier = GlanceModifier
                    .size(24.dp)
                    .cornerRadius(6.dp)
                    .background(GlanceTheme.colors.surface)
                    .clickable(
                        actionRunCallback<ToggleTaskActionCallback>(
                            actionParametersOf(widgetTaskIdKey to task.id)
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

            // Title and Due Time
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity<MainActivity>(actionParametersOf(openTaskIdActionKey to task.id)))
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
