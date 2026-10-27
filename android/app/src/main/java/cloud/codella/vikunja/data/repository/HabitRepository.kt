package cloud.codella.vikunja.data.repository

import cloud.codella.vikunja.core.database.VikunjaDatabase
import cloud.codella.vikunja.core.database.entity.HabitEntity
import cloud.codella.vikunja.core.database.entity.HabitLogEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HabitRepository(private val database: VikunjaDatabase) {

    val habitsFlow: Flow<List<HabitEntity>> = database.habitDao().getAllHabitsFlow()
    val allLogsFlow: Flow<List<HabitLogEntity>> = database.habitDao().getAllLogsFlow()

    fun getTodayString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    suspend fun createHabit(title: String, hexColor: String = "1973E8", iconName: String = "Check"): Long {
        val habit = HabitEntity(
            title = title.trim(),
            hexColor = hexColor,
            iconName = iconName,
            streak = 0
        )
        return database.habitDao().insertHabit(habit)
    }

    suspend fun toggleHabitToday(habit: HabitEntity) {
        val today = getTodayString()
        val dao = database.habitDao()
        if (habit.lastCompletedDate == today) {
            // Already completed today -> toggle off
            dao.deleteLog(habit.id, today)
            dao.updateHabit(habit.copy(
                streak = (habit.streak - 1).coerceAtLeast(0),
                lastCompletedDate = ""
            ))
        } else {
            // Mark completed today
            dao.insertLog(HabitLogEntity(habit.id, today))
            dao.updateHabit(habit.copy(
                streak = habit.streak + 1,
                lastCompletedDate = today
            ))
        }
    }

    suspend fun deleteHabit(id: Long) {
        database.habitDao().deleteHabit(id)
    }
}
