package cloud.codella.vikunja

import android.app.Application
import android.content.Intent
import androidx.work.*
import cloud.codella.vikunja.core.database.VikunjaDatabase
import cloud.codella.vikunja.core.datastore.PreferencesManager
import cloud.codella.vikunja.core.datastore.TokenManager
import cloud.codella.vikunja.core.network.NetworkClient
import cloud.codella.vikunja.core.network.VikunjaApiService
import cloud.codella.vikunja.core.notifications.AlarmScheduler
import cloud.codella.vikunja.core.notifications.DailySummaryWorker
import cloud.codella.vikunja.core.notifications.NotificationHelper
import cloud.codella.vikunja.core.notifications.PersistentNotificationService
import cloud.codella.vikunja.core.sync.SyncWorker
import cloud.codella.vikunja.data.repository.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class VikunjaApp : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set
    lateinit var tokenManager: TokenManager
        private set
    lateinit var database: VikunjaDatabase
        private set
    lateinit var networkClient: NetworkClient
        private set
    lateinit var notificationHelper: NotificationHelper
        private set
    lateinit var alarmScheduler: AlarmScheduler
        private set

    lateinit var authRepository: AuthRepository
        private set
    lateinit var projectRepository: ProjectRepository
        private set
    lateinit var taskRepository: TaskRepository
        private set
    lateinit var viewRepository: ViewRepository
        private set
    lateinit var syncRepository: SyncRepository
        private set
    lateinit var habitRepository: HabitRepository
        private set

    lateinit var cleanTaskRepository: cloud.codella.vikunja.domain.repository.TaskRepository
        private set
    lateinit var cleanProjectRepository: cloud.codella.vikunja.domain.repository.ProjectRepository
        private set
    lateinit var cleanTaskRelationRepository: cloud.codella.vikunja.domain.repository.TaskRelationRepository
        private set
    lateinit var getMyDayTasksUseCase: cloud.codella.vikunja.domain.usecase.GetMyDayTasksUseCase
        private set
    lateinit var toggleMyDayUseCase: cloud.codella.vikunja.domain.usecase.ToggleMyDayUseCase
        private set
    lateinit var resetMyDayUseCase: cloud.codella.vikunja.domain.usecase.ResetMyDayUseCase
        private set
    lateinit var pomodoroEngine: cloud.codella.vikunja.core.timer.PomodoroEngine
        private set
    lateinit var syncEngine: cloud.codella.vikunja.data.repository.sync.SyncEngine
        private set
    lateinit var tasksViewModelFactory: cloud.codella.vikunja.presentation.tasks.TasksViewModelFactory
        private set
    lateinit var cleanAlarmScheduler: cloud.codella.vikunja.core.alarm.AlarmScheduler
        private set
    lateinit var cleanNotificationHelper: cloud.codella.vikunja.core.notification.NotificationHelper
        private set
    lateinit var biometricAuthManager: cloud.codella.vikunja.core.security.BiometricAuthManager
        private set
    lateinit var securityPreferencesManager: cloud.codella.vikunja.core.security.SecurityPreferencesManager
        private set
    lateinit var appLockManager: cloud.codella.vikunja.presentation.security.AppLockManager
        private set

    val apiService: VikunjaApiService
        get() = networkClient.getApiService()

    override fun onCreate() {
        super.onCreate()

        preferencesManager = PreferencesManager(this)
        tokenManager = TokenManager(this)
        database = VikunjaDatabase.getInstance(this)
        networkClient = NetworkClient(preferencesManager, tokenManager)
        notificationHelper = NotificationHelper(this)
        alarmScheduler = AlarmScheduler(this)

        authRepository = AuthRepository(networkClient, tokenManager, preferencesManager, database)
        projectRepository = ProjectRepository(networkClient, database)
        taskRepository = TaskRepository(networkClient, database, alarmScheduler)
        viewRepository = ViewRepository(networkClient)
        syncRepository = SyncRepository(projectRepository, taskRepository, networkClient, database, notificationHelper)
        habitRepository = HabitRepository(database)

        // Initialize Phase 1 & 2 Clean Architecture components
        val cleanTaskRepo = TaskRepositoryImpl(database.taskDao(), database.syncQueueDao())
        val cleanProjectRepo = ProjectRepositoryImpl(database.projectDao(), database.syncQueueDao())
        val vikunjaRemoteClient = cloud.codella.vikunja.data.remote.client.VikunjaNetworkClient(preferencesManager, tokenManager)
        val engine = cloud.codella.vikunja.data.repository.sync.SyncEngine(
            syncQueueDao = database.syncQueueDao(),
            taskDao = database.taskDao(),
            projectDao = database.projectDao(),
            api = vikunjaRemoteClient.getApi()
        )

        cleanTaskRepository = cleanTaskRepo
        cleanProjectRepository = cleanProjectRepo
        cleanTaskRelationRepository = cloud.codella.vikunja.data.repository.TaskRelationRepositoryImpl(
            taskRelationDao = database.taskRelationDao(),
            syncQueueDao = database.syncQueueDao(),
            api = vikunjaRemoteClient.getApi()
        )
        getMyDayTasksUseCase = cloud.codella.vikunja.domain.usecase.GetMyDayTasksUseCase(cleanTaskRepo)
        toggleMyDayUseCase = cloud.codella.vikunja.domain.usecase.ToggleMyDayUseCase(cleanTaskRepo)
        resetMyDayUseCase = cloud.codella.vikunja.domain.usecase.ResetMyDayUseCase(cleanTaskRepo)
        pomodoroEngine = cloud.codella.vikunja.core.timer.PomodoroEngine(
            onPhaseTransition = { _, next ->
                val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 200, 100, 200), -1)
                }
            }
        )
        syncEngine = engine

        val getTasksUseCase = cloud.codella.vikunja.domain.usecase.GetTasksFlowUseCase(cleanTaskRepo)
        val createTaskUseCase = cloud.codella.vikunja.domain.usecase.CreateTaskUseCase(cleanTaskRepo)
        val toggleTaskDoneUseCase = cloud.codella.vikunja.domain.usecase.ToggleTaskDoneUseCase(cleanTaskRepo)
        val triggerSyncUseCase = cloud.codella.vikunja.domain.usecase.TriggerSyncUseCase(engine)

        tasksViewModelFactory = cloud.codella.vikunja.presentation.tasks.TasksViewModelFactory(
            getTasksFlowUseCase = getTasksUseCase,
            createTaskUseCase = createTaskUseCase,
            toggleTaskDoneUseCase = toggleTaskDoneUseCase,
            triggerSyncUseCase = triggerSyncUseCase,
            taskRepository = cleanTaskRepo,
            projectRepository = cleanProjectRepo,
            taskRelationRepository = cleanTaskRelationRepository
        )

        // Initialize Phase 3 Clean AlarmScheduler & NotificationHelper
        cleanAlarmScheduler = cloud.codella.vikunja.core.alarm.AlarmSchedulerImpl(this)
        cleanNotificationHelper = cloud.codella.vikunja.core.notification.NotificationHelper(this)

        // Initialize Biometric & Security Managers
        securityPreferencesManager = cloud.codella.vikunja.core.security.SecurityPreferencesManager(this)
        biometricAuthManager = cloud.codella.vikunja.core.security.BiometricAuthManager(this)
        appLockManager = cloud.codella.vikunja.presentation.security.AppLockManager(securityPreferencesManager)

        // Enqueue Phase 3 Clean WorkManager background sync
        cloud.codella.vikunja.data.work.SyncWorkScheduler.enqueuePeriodicSync(this)

        // Reactively update Glance Home Screen Widget whenever tasks mutate in Room
        CoroutineScope(Dispatchers.IO).launch {
            database.taskDao().getAllTasksFlow().collect {
                cloud.codella.vikunja.presentation.widget.VikunjaTodayWidget.updateAll(this@VikunjaApp)
            }
        }

        setupWorkManager()

        // Check if persistent service is enabled
        CoroutineScope(Dispatchers.Main).launch {
            val persistentEnabled = preferencesManager.persistentServiceFlow.first()
            if (persistentEnabled && tokenManager.isLoggedIn()) {
                startForegroundService(Intent(this@VikunjaApp, PersistentNotificationService::class.java))
            }
        }
    }

    fun setupWorkManager(intervalMinutes: Long = 15) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes, TimeUnit.MINUTES
        ).setConstraints(constraints).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "vikunja_periodic_sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncWorkRequest
        )

        val dailySummaryRequest = PeriodicWorkRequestBuilder<DailySummaryWorker>(
            24, TimeUnit.HOURS
        ).setConstraints(constraints).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "vikunja_daily_summary",
            ExistingPeriodicWorkPolicy.KEEP,
            dailySummaryRequest
        )

        val eveningReviewRequest = PeriodicWorkRequestBuilder<cloud.codella.vikunja.core.notifications.EveningReviewWorker>(
            24, TimeUnit.HOURS
        ).setConstraints(constraints).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "vikunja_evening_review",
            ExistingPeriodicWorkPolicy.KEEP,
            eveningReviewRequest
        )
    }
}
