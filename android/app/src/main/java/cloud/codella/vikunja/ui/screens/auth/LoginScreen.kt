package cloud.codella.vikunja.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cloud.codella.vikunja.R
import cloud.codella.vikunja.data.repository.AuthRepository
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

/** Translate raw exceptions into user-friendly messages. */
private fun friendlyError(e: Throwable, serverUrl: String): String {
    return when (e) {
        is UnknownHostException ->
            "Cannot find server '$serverUrl'.\n\n" +
            "• Check your device's internet connection\n" +
            "• Verify the server URL is correct\n" +
            "• Try connecting to Wi-Fi or mobile data"
        is ConnectException ->
            "Connection refused to '$serverUrl'.\n\n" +
            "• The server may be down or the port is blocked\n" +
            "• Check that the URL includes the correct port if needed"
        is SocketTimeoutException ->
            "Connection timed out.\n\n" +
            "• The server is not responding in time\n" +
            "• Check your network speed and server status"
        is HttpException -> when (e.code()) {
            401 -> "Incorrect username or password."
            403 -> "Access denied. Check your credentials."
            404 -> "Server not found at this URL. Check the server address."
            else -> "Server returned an error (HTTP ${e.code()})."
        }
        is javax.net.ssl.SSLException ->
            "SSL/TLS error connecting to server.\n\n" +
            "• If using a self-signed certificate, enable 'Allow self-signed certificate'\n" +
            "• Check that your server's HTTPS certificate is valid"
        else -> e.localizedMessage ?: "An unexpected error occurred. Please try again."
    }
}

@Composable
fun LoginScreen(
    authRepository: AuthRepository,
    onLoginSuccess: () -> Unit
) {
    var serverUrl by remember { mutableStateOf("https://tasks.codella.cloud") }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Credentials, 1 = API Token

    // Credentials
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }

    // API Token
    var apiToken by remember { mutableStateOf("") }

    var allowSelfSigned by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var serverStatus by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Vikunja",
                style = MaterialTheme.typography.headlineMedium
            )

            // Server URL field
            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    serverStatus = null
                },
                label = { Text(stringResource(R.string.server_url)) },
                leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Allow self signed switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.allow_self_signed),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = allowSelfSigned,
                    onCheckedChange = { allowSelfSigned = it }
                )
            }

            // Tab Selector
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.login_with_credentials)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.login_with_api_token)) }
                )
            }

            if (selectedTab == 0) {
                // Credentials Tab
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.username)) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = totpCode,
                    onValueChange = { totpCode = it },
                    label = { Text(stringResource(R.string.totp_token)) },
                    leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // API Token Tab
                OutlinedTextField(
                    value = apiToken,
                    onValueChange = { apiToken = it },
                    label = { Text(stringResource(R.string.api_token)) },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (errorMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { errorMessage = null },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        val result = if (selectedTab == 0) {
                            authRepository.loginWithCredentials(
                                serverUrl = serverUrl.trim(),
                                username = username.trim(),
                                pass = password,
                                totp = totpCode.trim().ifBlank { null },
                                allowSelfSigned = allowSelfSigned
                            )
                        } else {
                            authRepository.loginWithApiToken(
                                serverUrl = serverUrl.trim(),
                                token = apiToken.trim(),
                                allowSelfSigned = allowSelfSigned
                            )
                        }

                        isLoading = false
                        if (result.isSuccess) {
                            onLoginSuccess()
                        } else {
                            val ex = result.exceptionOrNull()
                            errorMessage = if (ex != null) {
                                friendlyError(ex, serverUrl.trim())
                            } else {
                                "Login failed. Please try again."
                            }
                        }
                    }
                },
                enabled = !isLoading && (
                    (selectedTab == 0 && username.isNotBlank() && password.isNotBlank()) ||
                    (selectedTab == 1 && apiToken.isNotBlank())
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.login))
                }
            }
        }
    }
}
