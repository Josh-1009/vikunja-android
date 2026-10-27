package cloud.codella.vikunja.ui.screens.tokens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cloud.codella.vikunja.R
import cloud.codella.vikunja.core.model.ApiToken
import cloud.codella.vikunja.core.model.ApiTokenCreateRequest
import cloud.codella.vikunja.core.network.VikunjaApiService
import kotlinx.coroutines.launch

@Composable
fun ApiTokensScreen(
    apiService: VikunjaApiService,
    onNavigateBack: () -> Unit
) {
    var tokens by remember { mutableStateOf<List<ApiToken>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newTokenTitle by remember { mutableStateOf("") }
    var generatedTokenString by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    fun loadTokens() {
        scope.launch {
            try {
                tokens = apiService.getApiTokens()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        loadTokens()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.api_tokens_mgmt)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Token")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tokens, key = { it.id }) { token ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(token.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Created: ${token.created.take(10)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    apiService.deleteApiToken(token.id)
                                    loadTokens()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Generate New API Token") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newTokenTitle,
                            onValueChange = { newTokenTitle = it },
                            label = { Text("Token Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newTokenTitle.isNotBlank()) {
                                scope.launch {
                                    val res = apiService.createApiToken(ApiTokenCreateRequest(newTokenTitle.trim()))
                                    generatedTokenString = res.token
                                    showCreateDialog = false
                                    newTokenTitle = ""
                                    loadTokens()
                                }
                            }
                        }
                    ) {
                        Text("Generate")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (generatedTokenString != null) {
            AlertDialog(
                onDismissRequest = { generatedTokenString = null },
                title = { Text("Token Generated") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Copy your new token now. You won't be able to see it again:")
                        SelectionContainer {
                            Text(
                                text = generatedTokenString!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { generatedTokenString = null }) {
                        Text("Done")
                    }
                }
            )
        }
    }
}
