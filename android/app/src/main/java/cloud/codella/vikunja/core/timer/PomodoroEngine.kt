package cloud.codella.vikunja.core.timer

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PomodoroMode(val displayName: String) {
    IDLE("Idle"),
    WORK("Work"),
    SHORT_BREAK("Short Break"),
    LONG_BREAK("Long Break")
}

data class PomodoroState(
    val mode: PomodoroMode = PomodoroMode.IDLE,
    val remainingSeconds: Int = 25 * 60,
    val totalSeconds: Int = 25 * 60,
    val currentCycle: Int = 1,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val taskId: Long? = null,
    val taskTitle: String? = null
)

class PomodoroEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val defaultWorkSec: Int = 25 * 60,
    private val defaultShortBreakSec: Int = 5 * 60,
    private val defaultLongBreakSec: Int = 15 * 60,
    private val cyclesBeforeLongBreak: Int = 4,
    private val onPhaseTransition: ((previous: PomodoroMode, next: PomodoroMode) -> Unit)? = null
) {
    private val _state = MutableStateFlow(
        PomodoroState(
            mode = PomodoroMode.IDLE,
            remainingSeconds = defaultWorkSec,
            totalSeconds = defaultWorkSec
        )
    )
    val state: StateFlow<PomodoroState> = _state.asStateFlow()

    private var tickerJob: Job? = null

    fun startWork(
        taskId: Long? = null,
        taskTitle: String? = null,
        durationSeconds: Int = defaultWorkSec
    ) {
        tickerJob?.cancel()
        val current = _state.value
        val assignedTask = taskId ?: current.taskId
        val assignedTitle = taskTitle ?: current.taskTitle

        _state.value = current.copy(
            mode = PomodoroMode.WORK,
            remainingSeconds = durationSeconds,
            totalSeconds = durationSeconds,
            isRunning = true,
            isPaused = false,
            taskId = assignedTask,
            taskTitle = assignedTitle
        )
        startTicker()
    }

    fun pause() {
        if (!_state.value.isRunning || _state.value.isPaused) return
        tickerJob?.cancel()
        _state.value = _state.value.copy(isPaused = true)
    }

    fun resume() {
        if (!_state.value.isRunning || !_state.value.isPaused) return
        _state.value = _state.value.copy(isPaused = false)
        startTicker()
    }

    fun stop() {
        tickerJob?.cancel()
        val previous = _state.value.mode
        _state.value = PomodoroState(
            mode = PomodoroMode.IDLE,
            remainingSeconds = defaultWorkSec,
            totalSeconds = defaultWorkSec,
            currentCycle = 1,
            isRunning = false,
            isPaused = false,
            taskId = null,
            taskTitle = null
        )
        if (previous != PomodoroMode.IDLE) {
            onPhaseTransition?.invoke(previous, PomodoroMode.IDLE)
        }
    }

    fun skip() {
        transitionToNextPhase()
    }

    fun reset() {
        tickerJob?.cancel()
        _state.value = PomodoroState(
            mode = PomodoroMode.IDLE,
            remainingSeconds = defaultWorkSec,
            totalSeconds = defaultWorkSec,
            currentCycle = 1,
            isRunning = false,
            isPaused = false
        )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && _state.value.remainingSeconds > 0) {
                delay(1000)
                val current = _state.value
                if (current.remainingSeconds > 0) {
                    _state.value = current.copy(remainingSeconds = current.remainingSeconds - 1)
                }
            }
            if (isActive && _state.value.remainingSeconds <= 0) {
                transitionToNextPhase()
            }
        }
    }

    private fun transitionToNextPhase() {
        tickerJob?.cancel()
        val current = _state.value
        val previousMode = current.mode

        val nextMode: PomodoroMode
        val nextDuration: Int
        var nextCycle = current.currentCycle

        when (current.mode) {
            PomodoroMode.WORK -> {
                if (current.currentCycle >= cyclesBeforeLongBreak) {
                    nextMode = PomodoroMode.LONG_BREAK
                    nextDuration = defaultLongBreakSec
                    nextCycle = 1 // Reset cycle after long break
                } else {
                    nextMode = PomodoroMode.SHORT_BREAK
                    nextDuration = defaultShortBreakSec
                    nextCycle = current.currentCycle + 1
                }
            }
            PomodoroMode.SHORT_BREAK, PomodoroMode.LONG_BREAK -> {
                nextMode = PomodoroMode.WORK
                nextDuration = defaultWorkSec
            }
            PomodoroMode.IDLE -> {
                nextMode = PomodoroMode.WORK
                nextDuration = defaultWorkSec
            }
        }

        _state.value = current.copy(
            mode = nextMode,
            remainingSeconds = nextDuration,
            totalSeconds = nextDuration,
            currentCycle = nextCycle,
            isRunning = true,
            isPaused = false
        )

        onPhaseTransition?.invoke(previousMode, nextMode)
        startTicker()
    }
}
