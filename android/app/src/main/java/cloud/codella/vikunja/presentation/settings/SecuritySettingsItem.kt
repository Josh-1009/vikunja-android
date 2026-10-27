package cloud.codella.vikunja.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import cloud.codella.vikunja.core.security.BiometricAuthManager
import cloud.codella.vikunja.core.security.BiometricStatus
import cloud.codella.vikunja.core.security.SecurityPreferencesManager
import cloud.codella.vikunja.presentation.security.AppLockManager
import kotlinx.coroutines.launch

/**
 * Settings section item allowing users to toggle Biometric / Screen Lock and configure auto-lock timeouts.
 */
@Composable
fun SecuritySettingsItem(
    securityPreferencesManager: SecurityPreferencesManager,
    biometricAuthManager: BiometricAuthManager,
    appLockManager: AppLockManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()

    val isBiometricEnabled by securityPreferencesManager.isBiometricEnabled.collectAsState(initial = false)
    val timeoutSeconds by securityPreferencesManager.lockTimeoutSeconds.collectAsState(initial = 0)

    val biometricStatus = remember { biometricAuthManager.canAuthenticate() }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val timeoutOptions = listOf(
        0 to "Immediately",
        30 to "30 seconds",
        60 to "1 minute",
        300 to "5 minutes"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Privacy & App Lock",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (biometricStatus != BiometricStatus.AVAILABLE) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Please set up a screen lock (PIN, Pattern, or Fingerprint) in your device settings to enable this feature.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Biometric Toggle Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Require Biometric / Screen Lock",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Require fingerprint, face, or device PIN to access tasks and projects",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isBiometricEnabled,
                    enabled = biometricStatus == BiometricStatus.AVAILABLE,
                    onCheckedChange = { targetState ->
                        if (activity != null) {
                            errorMessage = null
                            val actionTitle = if (targetState) "Enable App Lock" else "Disable App Lock"
                            appLockManager.isAuthenticating = true
                            biometricAuthManager.authenticate(
                                activity = activity,
                                title = actionTitle,
                                subtitle = "Confirm your identity to proceed",
                                onSuccess = {
                                    appLockManager.isAuthenticating = false
                                    scope.launch {
                                        securityPreferencesManager.setBiometricEnabled(targetState)
                                    }
                                },
                                onError = { error ->
                                    appLockManager.isAuthenticating = false
                                    errorMessage = error
                                }
                            )
                        }
                    }
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Auto-Lock Timeout Option (Only visible when biometric lock is enabled)
            if (isBiometricEnabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimeoutDialog = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LockClock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Lock Timeout",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        val currentLabel = timeoutOptions.find { it.first == timeoutSeconds }?.second ?: "Immediately"
                        Text(
                            text = currentLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            title = { Text("Auto-Lock Timeout") },
            text = {
                Column(modifier = Modifier.selectableGroup()) {
                    timeoutOptions.forEach { (seconds, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .selectable(
                                    selected = (seconds == timeoutSeconds),
                                    onClick = {
                                        scope.launch {
                                            securityPreferencesManager.setLockTimeoutSeconds(seconds)
                                            showTimeoutDialog = false
                                        }
                                    },
                                    role = Role.RadioButton
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (seconds == timeoutSeconds),
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimeoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
