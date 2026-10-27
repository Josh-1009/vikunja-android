package cloud.codella.vikunja.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val VikunjaBlue = Color(0xFF1973E8)
val VikunjaBlueDark = Color(0xFF8AB4F8)
val VikunjaCyan = Color(0xFF0EA5E9)

// Vikunja Priority Palette
val PriorityUrgent = Color(0xFFE11D48)   // 5 - Do Now
val PriorityHigh = Color(0xFFEA580C)     // 4 - Urgent
val PriorityMedium = Color(0xFF2563EB)   // 3 - High
val PriorityLow = Color(0xFF64748B)      // 2 - Medium
val PriorityNone = Color(0xFF94A3B8)     // 1 / 0 - Low / None

fun getPriorityColor(priority: Int): Color {
    return when (priority) {
        5 -> PriorityUrgent
        4 -> PriorityHigh
        3 -> PriorityMedium
        2 -> PriorityLow
        else -> Color.Transparent
    }
}

fun parseProjectColor(hex: String?, fallback: Color = VikunjaBlue): Color {
    if (hex.isNullOrBlank()) return fallback
    val clean = hex.removePrefix("#").trim()
    return try {
        when (clean.length) {
            6 -> Color(android.graphics.Color.parseColor("#$clean"))
            8 -> Color(android.graphics.Color.parseColor("#$clean"))
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

private val LightColorScheme = lightColorScheme(
    primary = VikunjaBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = VikunjaCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0)
)

private val DarkColorScheme = darkColorScheme(
    primary = VikunjaBlueDark,
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468C),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF003549),
    secondaryContainer = Color(0xFF004D68),
    onSecondaryContainer = Color(0xFFC2E8FF),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155)
)

private val AmoledColorScheme = darkColorScheme(
    primary = VikunjaBlueDark,
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468C),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF003549),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0A0F1D),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF161F30),
    onSurfaceVariant = Color(0xFFA0AEC0),
    outline = Color(0xFF2D3748),
    outlineVariant = Color(0xFF1A202C)
)

@Composable
fun VikunjaTheme(
    themeMode: String = "system",
    dynamicColor: Boolean = false, // Keep consistent Vikunja branding by default
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val colorScheme = when (themeMode) {
        "light" -> LightColorScheme
        "dark" -> DarkColorScheme
        "amoled" -> AmoledColorScheme
        else -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (systemDark) DarkColorScheme else LightColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
