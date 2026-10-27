package cloud.codella.vikunja

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import cloud.codella.vikunja.presentation.security.BiometricLockScreen
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import cloud.codella.vikunja.ui.navigation.Screen
import cloud.codella.vikunja.ui.screens.auth.LoginScreen
import cloud.codella.vikunja.ui.screens.calendar.CalendarScreen
import cloud.codella.vikunja.ui.screens.focus.FocusScreen
import cloud.codella.vikunja.ui.screens.habits.HabitsScreen
import cloud.codella.vikunja.ui.screens.matrix.MatrixScreen
import cloud.codella.vikunja.ui.screens.notifications.NotificationsScreen
import cloud.codella.vikunja.ui.screens.overview.OverviewScreen
import cloud.codella.vikunja.ui.screens.planday.PlanDayScreen
import cloud.codella.vikunja.ui.screens.project.ProjectDetailScreen
import cloud.codella.vikunja.ui.screens.quickadd.QuickAddBottomSheet
import cloud.codella.vikunja.ui.screens.settings.SettingsScreen
import cloud.codella.vikunja.ui.screens.task.TaskDetailScreen
import cloud.codella.vikunja.ui.screens.tokens.ApiTokensScreen
import cloud.codella.vikunja.ui.theme.VikunjaTheme

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_OPEN_QUICK_ADD = "cloud.codella.vikunja.EXTRA_OPEN_QUICK_ADD"
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    private val activeIntentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeIntentState.value = intent

        val app = application as VikunjaApp
        lifecycle.addObserver(app.appLockManager)
        app.appLockManager.checkInitialLockState()

        // Request POST_NOTIFICATIONS on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val themeMode by app.preferencesManager.themeModeFlow.collectAsState(initial = "system")
            val isAppLocked by app.appLockManager.isAppLocked.collectAsState()

            VikunjaTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    var showQuickAdd by remember { mutableStateOf(false) }

                    val startDestination = if (app.tokenManager.isLoggedIn()) {
                        Screen.Overview.route
                    } else {
                        Screen.Login.route
                    }

                    // Handle Deep Links & Quick Add Intents
                    val currentActiveIntent by activeIntentState
                    LaunchedEffect(currentActiveIntent) {
                        currentActiveIntent?.let { intentToProcess ->
                            handleDeepLink(intentToProcess) { route ->
                                if (route == "quick_add") {
                                    showQuickAdd = true
                                } else {
                                    navController.navigate(route)
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = startDestination
                        ) {
                            composable(Screen.Login.route) {
                                LoginScreen(
                                    authRepository = app.authRepository,
                                    onLoginSuccess = {
                                        navController.navigate(Screen.Overview.route) {
                                            popUpTo(Screen.Login.route) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable(Screen.Overview.route) {
                                OverviewScreen(
                                    taskRepository = app.taskRepository,
                                    projectRepository = app.projectRepository,
                                    preferencesManager = app.preferencesManager,
                                    onNavigateToProject = { projectId ->
                                        navController.navigate(Screen.ProjectDetail.createRoute(projectId))
                                    },
                                    onNavigateToTask = { taskId ->
                                        navController.navigate(Screen.TaskDetail.createRoute(taskId))
                                    },
                                    onNavigateToNotifications = {
                                        navController.navigate(Screen.Notifications.route)
                                    },
                                    onNavigateToSettings = {
                                        navController.navigate(Screen.Settings.route)
                                    },
                                    onNavigateToFocus = {
                                        navController.navigate(Screen.Focus.route)
                                    },
                                    onNavigateToMatrix = {
                                        navController.navigate(Screen.Matrix.route)
                                    },
                                    onNavigateToCalendar = {
                                        navController.navigate(Screen.Calendar.route)
                                    },
                                    onNavigateToHabits = {
                                        navController.navigate(Screen.Habits.route)
                                    },
                                    onNavigateToPlanDay = {
                                        navController.navigate(Screen.PlanDay.route)
                                    },
                                    onOpenQuickAdd = { showQuickAdd = true },
                                    onLoggedOut = {
                                        app.tokenManager.clear()
                                        navController.navigate(Screen.Login.route) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable(
                                route = Screen.ProjectDetail.route,
                                arguments = listOf(navArgument("projectId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val projectId = backStackEntry.arguments?.getLong("projectId") ?: 0L
                                ProjectDetailScreen(
                                    projectId = projectId,
                                    projectRepository = app.projectRepository,
                                    taskRepository = app.taskRepository,
                                    viewRepository = app.viewRepository,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToTask = { taskId ->
                                        navController.navigate(Screen.TaskDetail.createRoute(taskId))
                                    },
                                    onOpenQuickAdd = { showQuickAdd = true }
                                )
                            }

                            composable(
                                route = Screen.TaskDetail.route,
                                arguments = listOf(navArgument("taskId") { type = NavType.LongType })
                            ) { backStackEntry ->
                                val taskId = backStackEntry.arguments?.getLong("taskId") ?: 0L
                                TaskDetailScreen(
                                    taskId = taskId,
                                    taskRepository = app.taskRepository,
                                    apiService = app.apiService,
                                    taskRelationRepository = app.cleanTaskRelationRepository,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.Notifications.route) {
                                NotificationsScreen(
                                    apiService = app.apiService,
                                    database = app.database,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.Settings.route) {
                                SettingsScreen(
                                    authRepository = app.authRepository,
                                    preferencesManager = app.preferencesManager,
                                    securityPreferencesManager = app.securityPreferencesManager,
                                    biometricAuthManager = app.biometricAuthManager,
                                    appLockManager = app.appLockManager,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToTokens = { navController.navigate(Screen.ApiTokens.route) },
                                    onNavigateToBatteryGuide = { navController.navigate(Screen.BatteryGuide.route) },
                                    onLoggedOut = {
                                        navController.navigate(Screen.Login.route) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable(Screen.BatteryGuide.route) {
                                cloud.codella.vikunja.ui.screens.battery.BatteryGuideScreen(
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.ApiTokens.route) {
                                ApiTokensScreen(
                                    apiService = app.apiService,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.Focus.route) {
                                FocusScreen(
                                    taskRepository = app.taskRepository,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.Matrix.route) {
                                MatrixScreen(
                                    taskRepository = app.taskRepository,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToTask = { taskId ->
                                        navController.navigate(Screen.TaskDetail.createRoute(taskId))
                                    }
                                )
                            }

                            composable(Screen.Calendar.route) {
                                CalendarScreen(
                                    taskRepository = app.taskRepository,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToTask = { taskId ->
                                        navController.navigate(Screen.TaskDetail.createRoute(taskId))
                                    }
                                )
                            }

                            composable(Screen.Habits.route) {
                                HabitsScreen(
                                    habitRepository = app.habitRepository,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.PlanDay.route) {
                                cloud.codella.vikunja.presentation.planner.PlanDayScreen(
                                    taskRepository = app.cleanTaskRepository,
                                    getMyDayTasksUseCase = app.getMyDayTasksUseCase,
                                    toggleMyDayUseCase = app.toggleMyDayUseCase,
                                    onNavigateBack = { navController.popBackStack() },
                                    onNavigateToTask = { taskId ->
                                        navController.navigate(Screen.TaskDetail.createRoute(taskId))
                                    }
                                )
                            }
                        }

                        if (showQuickAdd) {
                            QuickAddBottomSheet(
                                taskRepository = app.taskRepository,
                                projectRepository = app.projectRepository,
                                onDismiss = { showQuickAdd = false }
                            )
                        }

                        if (isAppLocked) {
                            BiometricLockScreen(
                                biometricAuthManager = app.biometricAuthManager,
                                appLockManager = app.appLockManager,
                                onUnlocked = { app.appLockManager.unlock(this@MainActivity) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        activeIntentState.value = intent
    }

    private fun handleDeepLink(intent: Intent?, onNavigate: (String) -> Unit) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_OPEN_QUICK_ADD, false)) {
            onNavigate("quick_add")
            return
        }
        val extraTaskId = intent.getLongExtra("extra_task_id", -1L)
        if (extraTaskId != -1L) {
            onNavigate(Screen.TaskDetail.createRoute(extraTaskId))
            return
        }
        val data = intent.data ?: return
        if (data.scheme == "vikunja") {
            when (data.host) {
                "quick_add" -> onNavigate("quick_add")
                "today" -> onNavigate(Screen.Overview.route)
                "notifications" -> onNavigate(Screen.Notifications.route)
                "task" -> {
                    val taskId = data.lastPathSegment?.toLongOrNull()
                    if (taskId != null) {
                        onNavigate(Screen.TaskDetail.createRoute(taskId))
                    }
                }
            }
        }
    }
}
