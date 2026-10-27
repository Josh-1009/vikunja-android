package cloud.codella.vikunja.data.remote.client

import cloud.codella.vikunja.core.datastore.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

class JwtAuthInterceptor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = tokenManager.getToken()
        val path = original.url.encodedPath

        val request = if (!token.isNullOrBlank() && !path.endsWith("/login") && !path.endsWith("/info")) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        return chain.proceed(request)
    }
}
