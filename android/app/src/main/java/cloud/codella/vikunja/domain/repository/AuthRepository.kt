package cloud.codella.vikunja.domain.repository

import cloud.codella.vikunja.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeSession(): Flow<UserSession?>
    suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
        totp: String? = null,
        allowSelfSigned: Boolean = false
    ): Result<UserSession>

    suspend fun loginWithApiToken(
        serverUrl: String,
        token: String,
        allowSelfSigned: Boolean = false
    ): Result<UserSession>

    suspend fun logout(): Result<Unit>
    fun isLoggedIn(): Boolean
}
