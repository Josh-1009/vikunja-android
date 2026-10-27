package cloud.codella.vikunja.core.alarm

import cloud.codella.vikunja.domain.model.Task

interface AlarmScheduler {
    fun scheduleTaskReminder(task: Task): Boolean
    fun scheduleExactAlarm(taskId: Long, title: String, triggerAtMillis: Long, isDueDate: Boolean = true): Boolean
    fun cancelTaskReminder(taskId: Long, isDueDate: Boolean = true)
    fun canScheduleExactAlarms(): Boolean
}
