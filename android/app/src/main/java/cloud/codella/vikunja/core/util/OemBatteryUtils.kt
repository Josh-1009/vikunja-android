package cloud.codella.vikunja.core.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale

data class OemGuideInfo(
    val brandName: String,
    val summary: String,
    val instructions: List<String>,
    val autoStartIntent: Intent? = null
)

object OemBatteryUtils {

    val manufacturer: String
        get() = Build.MANUFACTURER.lowercase(Locale.ROOT)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }

    fun getBatteryOptimizationIntent(context: Context): Intent {
        return try {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } catch (_: Exception) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }

    fun getAppDetailsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun getOemGuideInfo(context: Context): OemGuideInfo {
        val brand = manufacturer
        return when {
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> {
                OemGuideInfo(
                    brandName = "Xiaomi / MIUI / HyperOS",
                    summary = "MIUI aggressively restricts background sync and alarms by default.",
                    instructions = listOf(
                        "1. Open App Info -> Enable 'Autostart' (allows alarms to wake the device).",
                        "2. Battery Saver -> Set to 'No restrictions'.",
                        "3. Lock Vikunja in the Recent Apps tray."
                    ),
                    autoStartIntent = findWorkingIntent(
                        context,
                        Intent().setComponent(
                            ComponentName(
                                "com.miui.securitycenter",
                                "com.miui.permcenter.autostart.AutoStartManagementActivity"
                            )
                        )
                    )
                )
            }
            brand.contains("samsung") -> {
                OemGuideInfo(
                    brandName = "Samsung One UI",
                    summary = "Samsung puts apps to sleep if not opened daily.",
                    instructions = listOf(
                        "1. Settings -> Apps -> Vikunja -> Battery -> Select 'Unrestricted'.",
                        "2. Device Care -> Battery -> Background usage limits: Remove Vikunja from 'Sleeping apps' and 'Deep sleeping apps'."
                    ),
                    autoStartIntent = findWorkingIntent(
                        context,
                        Intent().setComponent(
                            ComponentName(
                                "com.samsung.android.lool",
                                "com.samsung.android.sm.battery.ui.BatteryActivity"
                            )
                        )
                    )
                )
            }
            brand.contains("huawei") || brand.contains("honor") -> {
                OemGuideInfo(
                    brandName = "Huawei / Honor EMUI",
                    summary = "Huawei terminates background processes aggressively.",
                    instructions = listOf(
                        "1. Settings -> Apps -> App Launch -> Find Vikunja.",
                        "2. Toggle 'Manage Automatically' OFF.",
                        "3. Turn ON: 'Auto-launch', 'Secondary launch', and 'Run in background'."
                    ),
                    autoStartIntent = findWorkingIntent(
                        context,
                        Intent().setComponent(
                            ComponentName(
                                "com.huawei.systemmanager",
                                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                            )
                        )
                    )
                )
            }
            brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus") -> {
                OemGuideInfo(
                    brandName = "Oppo / Realme / OnePlus (ColorOS/OxygenOS)",
                    summary = "Requires Background Activity and Autostart permission.",
                    instructions = listOf(
                        "1. App Info -> Battery Usage -> Enable 'Allow background activity'.",
                        "2. Enable 'Auto-launch' in Startup permissions.",
                        "3. In Recent Apps overview, tap the 3 dots and tap 'Lock'."
                    ),
                    autoStartIntent = findWorkingIntent(
                        context,
                        Intent().setComponent(
                            ComponentName(
                                "com.coloros.safecenter",
                                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                            )
                        )
                    )
                )
            }
            brand.contains("vivo") || brand.contains("iqoo") -> {
                OemGuideInfo(
                    brandName = "Vivo / Funtouch OS",
                    summary = "Vivo stops background alarms when battery saver is on.",
                    instructions = listOf(
                        "1. Settings -> Battery -> High background power consumption -> Enable Vikunja.",
                        "2. Settings -> More Settings -> Applications -> Autostart -> Enable Vikunja."
                    ),
                    autoStartIntent = findWorkingIntent(
                        context,
                        Intent().setComponent(
                            ComponentName(
                                "com.iqoo.secure",
                                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                            )
                        )
                    )
                )
            }
            else -> {
                OemGuideInfo(
                    brandName = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
                    summary = "Standard Android battery optimization rules apply.",
                    instructions = listOf(
                        "1. Disable Battery Optimization in system settings.",
                        "2. Ensure Notifications and Exact Alarms permissions are enabled."
                    ),
                    autoStartIntent = null
                )
            }
        }
    }

    private fun findWorkingIntent(context: Context, intent: Intent): Intent? {
        return try {
            val activities = context.packageManager.queryIntentActivities(intent, 0)
            if (activities.isNotEmpty()) intent else null
        } catch (_: Exception) {
            null
        }
    }
}
