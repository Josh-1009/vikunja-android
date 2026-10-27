package cloud.codella.vikunja.core.alarm

import android.app.AlarmManager
import android.content.Context
import cloud.codella.vikunja.domain.model.Task
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AlarmSchedulerTest {

    private val context = mockk<Context>(relaxed = true)
    private val alarmManager = mockk<AlarmManager>(relaxed = true)
    private lateinit var scheduler: AlarmSchedulerImpl

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0
        every { context.getSystemService(Context.ALARM_SERVICE) } returns alarmManager
        scheduler = AlarmSchedulerImpl(context)
    }

    @Test
    fun `scheduleTaskReminder returns false when task is already done`() {
        val task = Task(
            id = 1L,
            projectId = 10L,
            title = "Completed Task",
            done = true,
            dueDate = Instant.now().plusSeconds(3600).toString()
        )

        val result = scheduler.scheduleTaskReminder(task)

        assertFalse(result)
        verify { alarmManager wasNot Called }
    }

    @Test
    fun `scheduleTaskReminder returns false when task has no due date`() {
        val task = Task(
            id = 2L,
            projectId = 10L,
            title = "Task without Due Date",
            done = false,
            dueDate = null
        )

        val result = scheduler.scheduleTaskReminder(task)

        assertFalse(result)
        verify { alarmManager wasNot Called }
    }

    @Test
    fun `scheduleTaskReminder returns false when due date is in the past`() {
        val pastDate = Instant.now().minusSeconds(3600).toString()
        val task = Task(
            id = 3L,
            projectId = 10L,
            title = "Past Task",
            done = false,
            dueDate = pastDate
        )

        val result = scheduler.scheduleTaskReminder(task)

        assertFalse(result)
        verify { alarmManager wasNot Called }
    }

    @Test
    fun `scheduleTaskReminder returns false on malformed date string`() {
        val task = Task(
            id = 4L,
            projectId = 10L,
            title = "Malformed Date Task",
            done = false,
            dueDate = "not-a-valid-iso-date"
        )

        val result = scheduler.scheduleTaskReminder(task)

        assertFalse(result)
        verify { alarmManager wasNot Called }
    }
}
