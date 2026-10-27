package cloud.codella.vikunja.ui.screens.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cloud.codella.vikunja.R
import cloud.codella.vikunja.core.database.VikunjaDatabase
import cloud.codella.vikunja.core.database.entity.NotificationEntity
import cloud.codella.vikunja.core.network.VikunjaApiService
import kotlinx.coroutines.launch

@Composable
fun NotificationsScreen(
    apiService: VikunjaApiService,
    database: VikunjaDatabase,
    onNavigateBack: () -> Unit
) {
    val notifications by database.notificationDao().getAllNotificationsFlow().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_notifications)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    apiService.clearNotifications()
                                    database.notificationDao().clearAll()
                                } catch (_: Exception) {}
                            }
                        }
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All")
                    }
                }
            )
        }
    ) { padding ->
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No notifications right now",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall)
                                Text(item.subject, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    item.created.take(16).replace("T", " "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!item.read) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                apiService.markNotificationRead(item.id)
                                                database.notificationDao().markAsRead(item.id)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Mark read")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
