package cloud.codella.vikunja.data.remote.client

import cloud.codella.vikunja.core.datastore.PreferencesManager
import cloud.codella.vikunja.core.datastore.TokenManager
import cloud.codella.vikunja.data.remote.dto.TokenResponseDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

class TokenRefreshAuthenticator(
    private val tokenManager: TokenManager,
    private val preferencesManager: PreferencesManager,
    private val json: Json,
    private val unauthenticatedClientProvider: () -> OkHttpClient
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        if (path.contains("/user/token/refresh") ||
            path.contains("/login") ||
            responseCount(response) >= 3
        ) {
            return null
        }

        if (tokenManager.isApiToken()) {
            return null
        }

        val staleToken = tokenManager.getToken() ?: return null

        return runBlocking {
            refreshMutex.withLock {
                val currentToken = tokenManager.getToken()
                // If another concurrent request refreshed the token while we were awaiting the mutex
                if (!currentToken.isNullOrBlank() && currentToken != staleToken) {
                    return@runBlocking response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                }

                try {
                    val serverUrl = preferencesManager.serverUrlFlow.first()
                        .ifBlank { "https://tasks.codella.cloud" }
                    val cleanBase = serverUrl.trimEnd('/')
                    val refreshUrl = "$cleanBase/api/v1/user/token/refresh"

                    val refreshRequest = Request.Builder()
                        .url(refreshUrl)
                        .post(ByteArray(0).toRequestBody(null, 0, 0))
                        .header("Authorization", "Bearer $staleToken")
                        .build()

                    val client = unauthenticatedClientProvider()
                    client.newCall(refreshRequest).execute().use { refreshResponse ->
                        if (refreshResponse.isSuccessful) {
                            val bodyString = refreshResponse.body?.string()
                            if (!bodyString.isNullOrBlank()) {
                                val tokenObj = json.decodeFromString<TokenResponseDto>(bodyString)
                                tokenManager.saveToken(tokenObj.token, isApiToken = false)
                                return@runBlocking response.request.newBuilder()
                                    .header("Authorization", "Bearer ${tokenObj.token}")
                                    .build()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
