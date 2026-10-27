package cloud.codella.vikunja.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import cloud.codella.vikunja.core.util.AppUpdateInfo
import cloud.codella.vikunja.core.util.AppUpdateManager
import cloud.codella.vikunja.core.util.GoogleCalendarHelper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.codella.vikunja.R
import cloud.codella.vikunja.VikunjaApp
import cloud.codella.vikunja.core.datastore.PreferencesManager
import cloud.codella.vikunja.core.notifications.PersistentNotificationService
import cloud.codella.vikunja.data.repository.AuthRepository
import cloud.codella.vikunja.core.security.BiometricAuthManager
import cloud.codella.vikunja.core.security.SecurityPreferencesManager
import cloud.codella.vikunja.presentation.security.AppLockManager
import cloud.codella.vikunja.presentation.settings.SecuritySettingsItem
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    authRepository: AuthRepository,
    preferencesManager: PreferencesManager,
    securityPreferencesManager: SecurityPreferencesManager,
    biometricAuthManager: BiometricAuthManager,
    appLockManager: AppLockManager,
    onNavigateBack: () -> Unit,
    onNavigateToTokens: () -> Unit,
    onNavigateToBatteryGuide: () -> Unit,
    onLoggedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentTheme by preferencesManager.themeModeFlow.collectAsState(initial = "system")
    val currentLang by preferencesManager.languageFlow.collectAsState(initial = "system")
    val syncInterval by preferencesManager.syncIntervalFlow.collectAsState(initial = 15)
    val persistentService by preferencesManager.persistentServiceFlow.collectAsState(initial = false)
    val popupEnabled by preferencesManager.popupReminderEnabledFlow.collectAsState(initial = true)
    val persistentNotifEnabled by preferencesManager.persistentNotifEnabledFlow.collectAsState(initial = true)
    val persistentMinPriority by preferencesManager.persistentMinPriorityFlow.collectAsState(initial = 0)
    val activeUsername by preferencesManager.activeUsernameFlow.collectAsState(initial = "")
    val serverUrl by preferencesManager.serverUrlFlow.collectAsState(initial = "")

    var showBatteryGuide by remember { mutableStateOf(false) }
    var updateChannel by remember { mutableStateOf("Server") }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfoDialog by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showNoUpdateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User & Server Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (activeUsername.isNotBlank()) activeUsername else "Logged in",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = serverUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Theme Setting
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "system" to stringResource(R.string.theme_system),
                    "light" to stringResource(R.string.theme_light),
                    "dark" to stringResource(R.string.theme_dark),
                    "amoled" to stringResource(R.string.theme_amoled)
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = currentTheme == mode,
                        onClick = { scope.launch { preferencesManager.setThemeMode(mode) } },
                        label = { Text(label) }
                    )
                }
            }

            HorizontalDivider()

            // Language Setting
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "system" to stringResource(R.string.lang_system),
                    "en" to stringResource(R.string.lang_en),
                    "ar" to stringResource(R.string.lang_ar)
                ).forEach { (lang, label) ->
                    FilterChip(
                        selected = currentLang == lang,
                        onClick = { scope.launch { preferencesManager.setLanguage(lang) } },
                        label = { Text(label) }
                    )
                }
            }

            HorizontalDivider()

            // Notifications & Customization Section
            Text(
                text = "Notifications & Reminders",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )

            // 1. TickTick-Style Full-Screen Pop-up Reminder Toggle
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.popup_reminders_toggle), style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.popup_reminders_toggle_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = popupEnabled,
                            onCheckedChange = { scope.launch { preferencesManager.setPopupReminderEnabled(it) } }
                        )
                    }

                    // Quick Test Reminder Button
                    OutlinedButton(
                        onClick = {
                            val helper = cloud.codella.vikunja.core.notifications.NotificationHelper(context)
                            helper.showTaskNotification(
                                taskId = 9999L,
                                title = "Test Reminder (TickTick Pop-up)",
                                body = "This is how full-screen task alerts look and sound!",
                                isDueDate = true
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Preview Pop-up Reminder Now")
                    }
                }
            }

            // 2. Persistent Foreground Status Bar Notification Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.persistent_notif_toggle), style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            Text(
                                stringResource(R.string.persistent_notif_toggle_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = persistentService,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    preferencesManager.setPersistentService(enabled)
                                    val intent = Intent(context, PersistentNotificationService::class.java)
                                    if (enabled) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.stopService(intent)
                                    }
                                }
                            }
                        )
                    }

                    if (persistentService) {
                        Text(
                            text = stringResource(R.string.persistent_min_priority_label),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                0 to "All Today",
                                3 to "Medium+ (≥3)",
                                5 to "Urgent Only (5)"
                            ).forEach { (p, label) ->
                                FilterChip(
                                    selected = persistentMinPriority == p,
                                    onClick = { scope.launch { preferencesManager.setPersistentMinPriority(p) } },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Nagging / Persistent Repeat Alarm Card
            val naggingAlarmEnabled by preferencesManager.naggingAlarmEnabledFlow.collectAsState(initial = true)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.nagging_alarm_title), style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.nagging_alarm_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = naggingAlarmEnabled,
                        onCheckedChange = { scope.launch { preferencesManager.setNaggingAlarmEnabled(it) } }
                    )
                }
            }

            // 4. Daily Morning Briefing & Evening Review Card
            val morningBriefingEnabled by preferencesManager.morningBriefingEnabledFlow.collectAsState(initial = true)
            val eveningReviewEnabled by preferencesManager.eveningReviewEnabledFlow.collectAsState(initial = true)
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.briefings_title), style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.morning_briefing_toggle), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.morning_briefing_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = morningBriefingEnabled,
                            onCheckedChange = { scope.launch { preferencesManager.setMorningBriefingEnabled(it) } }
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.evening_review_toggle), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.evening_review_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = eveningReviewEnabled,
                            onCheckedChange = { scope.launch { preferencesManager.setEveningReviewEnabled(it) } }
                        )
                    }
                }
            }

            // 5. Battery Optimization Guide Tile
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToBatteryGuide)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.battery_optimization), style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(
                                stringResource(R.string.battery_optimization_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            HorizontalDivider()

            // Privacy & App Lock
            SecuritySettingsItem(
                securityPreferencesManager = securityPreferencesManager,
                biometricAuthManager = biometricAuthManager,
                appLockManager = appLockManager
            )

            HorizontalDivider()

            // API Tokens Screen Link
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToTokens),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VpnKey, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.api_tokens_mgmt), style = MaterialTheme.typography.bodyLarge)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            HorizontalDivider()

            // Google Calendar Integration Card
            Text(
                text = stringResource(R.string.gcal_sync_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Sync with Google Calendar", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    }
                    Text(
                        "Subscribe to your Vikunja tasks in Google Calendar via your private iCal link, or use the 'Add to Google Calendar' button directly inside any task.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val icalUrl = GoogleCalendarHelper.getIcalUrl(serverUrl)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Vikunja iCal Feed", icalUrl)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "iCal URL copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.gcal_copy_ical), fontSize = 12.sp)
                        }
                        Button(
                            onClick = { GoogleCalendarHelper.openGoogleCalendarWebSettings(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.gcal_open_web), fontSize = 12.sp)
                        }
                    }
                }
            }

            HorizontalDivider()

            // App Updates & Channel Selection Card
            Text(
                text = stringResource(R.string.app_update_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Current Version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("v1.0.0 (Build 1)", style = MaterialTheme.typography.titleSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Server", "GitHub").forEach { ch ->
                                FilterChip(
                                    selected = updateChannel == ch,
                                    onClick = { updateChannel = ch },
                                    label = { Text(ch) }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            isCheckingUpdate = true
                            scope.launch {
                                val result = if (updateChannel == "Server") {
                                    AppUpdateManager.checkServerUpdate(serverUrl, currentVersionCode = 1)
                                } else {
                                    AppUpdateManager.checkGitHubRelease("Josh-1009/vikunja-android", currentVersionName = "1.0.0")
                                }
                                isCheckingUpdate = false

                                result.onSuccess { info ->
                                    if (info.isUpdateAvailable) {
                                        updateInfoDialog = info
                                    } else {
                                        showNoUpdateDialog = true
                                    }
                                }.onFailure { err ->
                                    Toast.makeText(context, "Update check: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isCheckingUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking...")
                        } else {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.app_update_check))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logout Button
            Button(
                onClick = {
                    scope.launch {
                        authRepository.logout()
                        onLoggedOut()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.logout))
            }
        }

        if (showBatteryGuide) {
            AlertDialog(
                onDismissRequest = { showBatteryGuide = false },
                title = { Text(stringResource(R.string.battery_optimization)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "To ensure notifications (due dates and reminders) fire on time without delay:\n\n" +
                            "• Xiaomi / MIUI / HyperOS: Enable 'Autostart' and set Battery Saver to 'No restrictions'.\n\n" +
                            "• Samsung: Exclude Vikunja from 'Sleeping apps' and disable 'Put unused apps to sleep'.\n\n" +
                            "• Oppo / Realme / Vivo: Lock app in recent tasks and allow background activity."
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBatteryGuide = false
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        }
                    ) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatteryGuide = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // New Update Available Dialog
        if (updateInfoDialog != null) {
            val info = updateInfoDialog!!
            AlertDialog(
                onDismissRequest = { updateInfoDialog = null },
                icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text(stringResource(R.string.app_update_available)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("New Version: v${info.latestVersionName}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("Release Notes:", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Text(
                                text = info.changelog,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            AppUpdateManager.startDownloadAndInstall(context, info.downloadUrl, info.latestVersionName)
                            updateInfoDialog = null
                        }
                    ) {
                        Text("Download & Install")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { updateInfoDialog = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // Up to Date Dialog
        if (showNoUpdateDialog) {
            AlertDialog(
                onDismissRequest = { showNoUpdateDialog = false },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981)) },
                title = { Text("Up to Date") },
                text = { Text(stringResource(R.string.app_update_latest)) },
                confirmButton = {
                    Button(onClick = { showNoUpdateDialog = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
