package cloud.codella.vikunja.data.repository

import cloud.codella.vikunja.core.datastore.PreferencesManager
import cloud.codella.vikunja.core.datastore.TokenManager
import cloud.codella.vikunja.data.remote.client.VikunjaNetworkClient
import cloud.codella.vikunja.data.remote.dto.RemoteLoginRequest
import cloud.codella.vikunja.domain.model.UserSession
import cloud.codella.vikunja.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class AuthRepositoryImpl(
    private val networkClient: VikunjaNetworkClient,
    private val tokenManager: TokenManager,
    private val preferencesManager: PreferencesManager
) : AuthRepository {

    override fun observeSession(): Flow<UserSession?> {
        return combine(
            preferencesManager.activeUserIdFlow,
            preferencesManager.activeUsernameFlow,
            preferencesManager.serverUrlFlow,
            preferencesManager.allowSelfSignedFlow
        ) { userId: Long, username: String, serverUrl: String, allowSelfSigned: Boolean ->
            val token = tokenManager.getToken()
            if (token != null && serverUrl.isNotBlank()) {
                UserSession(
                    userId = userId,
                    username = username,
                    token = token,
                    isApiToken = tokenManager.isApiToken(),
                    serverUrl = serverUrl,
                    allowSelfSigned = allowSelfSigned
                )
            } else {
                null
            }
        }
    }

    private fun normalizeServerUrl(url: String): String {
        var trimmed = url.trim().trimEnd('/')
        if (trimmed.endsWith("/api/v1")) {
            trimmed = trimmed.removeSuffix("/api/v1").trimEnd('/')
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://$trimmed"
        }
        return trimmed
    }

    override suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
        totp: String?,
        allowSelfSigned: Boolean
    ): Result<UserSession> {
        val cleanUrl = normalizeServerUrl(serverUrl)
        return try {
            preferencesManager.setServerUrl(cleanUrl)
            preferencesManager.setAllowSelfSigned(allowSelfSigned)
            networkClient.invalidate()

            val api = networkClient.getApi()
            val resp = api.login(
                RemoteLoginRequest(
                    username = username.trim(),
                    password = password,
                    totpPasscode = totp?.trim()?.ifBlank { null },
                    longToken = true
                )
            )
            tokenManager.saveToken(resp.token, isApiToken = false)

            val user = try {
                api.getCurrentUser()
            } catch (_: Exception) {
                null
            }

            val uid = user?.id ?: 0L
            val uName = user?.username?.ifBlank { username.trim() } ?: username.trim()
            preferencesManager.setActiveUser(uid, uName)

            Result.success(
                UserSession(
                    userId = uid,
                    username = uName,
                    token = resp.token,
                    isApiToken = false,
                    serverUrl = cleanUrl,
                    allowSelfSigned = allowSelfSigned
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loginWithApiToken(
        serverUrl: String,
        token: String,
        allowSelfSigned: Boolean
    ): Result<UserSession> {
        val cleanUrl = normalizeServerUrl(serverUrl)
        val cleanToken = if (token.startsWith("Bearer ", ignoreCase = true)) {
            token.substring(7).trim()
        } else {
            token.trim()
        }
        return try {
            preferencesManager.setServerUrl(cleanUrl)
            preferencesManager.setAllowSelfSigned(allowSelfSigned)
            tokenManager.saveToken(cleanToken, isApiToken = true)
            networkClient.invalidate()

            val api = networkClient.getApi()
            val user = try {
                api.getCurrentUser()
            } catch (_: Exception) {
                null
            }

            val uid = user?.id ?: 0L
            val uName = user?.username ?: "API Token User"
            preferencesManager.setActiveUser(uid, uName)

            Result.success(
                UserSession(
                    userId = uid,
                    username = uName,
                    token = cleanToken,
                    isApiToken = true,
                    serverUrl = cleanUrl,
                    allowSelfSigned = allowSelfSigned
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            try {
                networkClient.getApi().logout()
            } catch (_: Exception) {}
            tokenManager.clear()
            preferencesManager.clearUser()
            networkClient.invalidate()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun isLoggedIn(): Boolean = tokenManager.isLoggedIn()
}
