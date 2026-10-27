package cloud.codella.vikunja.core.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PomodoroEngineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private var lastTransition: Pair<PomodoroMode, PomodoroMode>? = null
    private lateinit var engine: PomodoroEngine

    @Before
    fun setup() {
        lastTransition = null
        engine = PomodoroEngine(
            scope = testScope,
            defaultWorkSec = 25 * 60,
            defaultShortBreakSec = 5 * 60,
            defaultLongBreakSec = 15 * 60,
            cyclesBeforeLongBreak = 4,
            onPhaseTransition = { prev, next ->
                lastTransition = Pair(prev, next)
            }
        )
    }

    @Test
    fun initialState_isIdle() {
        val state = engine.state.value
        assertEquals(PomodoroMode.IDLE, state.mode)
        assertFalse(state.isRunning)
        assertFalse(state.isPaused)
        assertEquals(1, state.currentCycle)
        assertEquals(25 * 60, state.remainingSeconds)
    }

    @Test
    fun startWork_transitionsToWorkMode() {
        engine.startWork(taskId = 42L, taskTitle = "Deep Work Task")
        val state = engine.state.value

        assertEquals(PomodoroMode.WORK, state.mode)
        assertTrue(state.isRunning)
        assertFalse(state.isPaused)
        assertEquals(42L, state.taskId)
        assertEquals("Deep Work Task", state.taskTitle)
        assertEquals(25 * 60, state.totalSeconds)
    }

    @Test
    fun pauseAndResume_updatesPausedState() {
        engine.startWork()
        assertTrue(engine.state.value.isRunning)
        assertFalse(engine.state.value.isPaused)

        engine.pause()
        assertTrue(engine.state.value.isPaused)

        engine.resume()
        assertFalse(engine.state.value.isPaused)
    }

    @Test
    fun skip_cyclesThroughShortBreaksAndLongBreak() {
        // Cycle 1: Work -> Short Break (advance to cycle 2)
        engine.startWork()
        assertEquals(PomodoroMode.WORK, engine.state.value.mode)
        assertEquals(1, engine.state.value.currentCycle)

        engine.skip()
        assertEquals(PomodoroMode.SHORT_BREAK, engine.state.value.mode)
        assertEquals(2, engine.state.value.currentCycle)
        assertEquals(5 * 60, engine.state.value.remainingSeconds)
        assertEquals(Pair(PomodoroMode.WORK, PomodoroMode.SHORT_BREAK), lastTransition)

        // Short Break -> Cycle 2 Work
        engine.skip()
        assertEquals(PomodoroMode.WORK, engine.state.value.mode)
        assertEquals(2, engine.state.value.currentCycle)

        // Cycle 2: Work -> Short Break (advance to cycle 3)
        engine.skip()
        assertEquals(PomodoroMode.SHORT_BREAK, engine.state.value.mode)
        assertEquals(3, engine.state.value.currentCycle)

        // Short Break -> Cycle 3 Work
        engine.skip()
        assertEquals(PomodoroMode.WORK, engine.state.value.mode)
        assertEquals(3, engine.state.value.currentCycle)

        // Cycle 3: Work -> Short Break (advance to cycle 4)
        engine.skip()
        assertEquals(PomodoroMode.SHORT_BREAK, engine.state.value.mode)
        assertEquals(4, engine.state.value.currentCycle)

        // Short Break -> Cycle 4 Work
        engine.skip()
        assertEquals(PomodoroMode.WORK, engine.state.value.mode)
        assertEquals(4, engine.state.value.currentCycle)

        // Cycle 4: Work -> 4th cycle reached! Transitions to Long Break and resets cycle to 1
        engine.skip()
        assertEquals(PomodoroMode.LONG_BREAK, engine.state.value.mode)
        assertEquals(1, engine.state.value.currentCycle)
        assertEquals(15 * 60, engine.state.value.remainingSeconds)
        assertEquals(Pair(PomodoroMode.WORK, PomodoroMode.LONG_BREAK), lastTransition)

        // Long Break -> Cycle 1 Work
        engine.skip()
        assertEquals(PomodoroMode.WORK, engine.state.value.mode)
        assertEquals(1, engine.state.value.currentCycle)
    }

    @Test
    fun stop_resetsToIdle() {
        engine.startWork(taskId = 10L, taskTitle = "Important")
        engine.stop()

        val state = engine.state.value
        assertEquals(PomodoroMode.IDLE, state.mode)
        assertFalse(state.isRunning)
        assertFalse(state.isPaused)
        assertNull(state.taskId)
        assertNull(state.taskTitle)
    }
}
