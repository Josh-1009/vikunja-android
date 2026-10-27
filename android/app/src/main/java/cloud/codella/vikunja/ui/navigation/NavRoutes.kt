package cloud.codella.vikunja.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Overview : Screen("overview")
    object ProjectDetail : Screen("project/{projectId}") {
        fun createRoute(projectId: Long) = "project/$projectId"
    }
    object TaskDetail : Screen("task/{taskId}") {
        fun createRoute(taskId: Long) = "task/$taskId"
    }
    object Notifications : Screen("notifications")
    object Settings : Screen("settings")
    object ApiTokens : Screen("tokens")
    object BatteryGuide : Screen("battery_guide")

    // TickTick-inspired power productivity screens
    object Focus : Screen("focus")
    object Matrix : Screen("matrix")
    object Calendar : Screen("calendar")
    object Habits : Screen("habits")
    object PlanDay : Screen("plan_day")
}
