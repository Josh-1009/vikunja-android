package cloud.codella.vikunja.core.network

import cloud.codella.vikunja.core.datastore.PreferencesManager
import cloud.codella.vikunja.core.datastore.TokenManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import cloud.codella.vikunja.core.model.LoginResponse
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class NetworkClient(
    private val preferencesManager: PreferencesManager,
    private val tokenManager: TokenManager
) {
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    private var currentRetrofit: Retrofit? = null
    private var currentBaseUrl: String = ""

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = tokenManager.getToken()
        val requestBuilder = original.newBuilder()

        if (!token.isNullOrBlank() && !original.url.encodedPath.endsWith("/login")) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        chain.proceed(requestBuilder.build())
    }

    private val tokenAuthenticator = Authenticator { _: Route?, response: Response ->
        if (responseCount(response) >= 2) {
            return@Authenticator null
        }
        val currentToken = tokenManager.getToken()
        if (currentToken.isNullOrBlank() || tokenManager.isApiToken()) {
            return@Authenticator null
        }

        synchronized(this) {
            val freshToken = tokenManager.getToken()
            if (freshToken != currentToken && !freshToken.isNullOrBlank()) {
                return@Authenticator response.request.newBuilder()
                    .header("Authorization", "Bearer $freshToken")
                    .build()
            }

            try {
                val serverUrl = currentBaseUrl.ifBlank { "https://tasks.codella.cloud/" }
                val refreshUrl = (if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/") + "api/v1/user/token/refresh"
                val refreshRequest = okhttp3.Request.Builder()
                    .url(refreshUrl)
                    .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                    .header("Authorization", "Bearer $currentToken")
                    .build()

                val refreshClient = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

                val refreshResponse = refreshClient.newCall(refreshRequest).execute()
                if (refreshResponse.isSuccessful) {
                    val bodyString = refreshResponse.body?.string()
                    if (!bodyString.isNullOrBlank()) {
                        val loginResp = json.decodeFromString<LoginResponse>(bodyString)
                        tokenManager.saveToken(loginResp.token, isApiToken = false)
                        return@Authenticator response.request.newBuilder()
                            .header("Authorization", "Bearer ${loginResp.token}")
                            .build()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }

    fun getApiService(): VikunjaApiService {
        val serverUrl = runBlocking { preferencesManager.serverUrlFlow.first() }
        val allowSelfSigned = runBlocking { preferencesManager.allowSelfSignedFlow.first() }

        if (currentRetrofit != null && currentBaseUrl == serverUrl) {
            return currentRetrofit!!.create(VikunjaApiService::class.java)
        }

        val okHttpClient = buildOkHttpClient(allowSelfSigned)
        val normalizedUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"

        currentRetrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        currentBaseUrl = serverUrl

        return currentRetrofit!!.create(VikunjaApiService::class.java)
    }

    /** Force-clear the cached Retrofit instance so the next call rebuilds with fresh URL/SSL settings. */
    fun invalidate() {
        currentRetrofit = null
        currentBaseUrl = ""
    }

    private fun buildOkHttpClient(allowSelfSigned: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })

        if (allowSelfSigned) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("SSL").apply {
                    init(null, trustAllCerts, SecureRandom())
                }
                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return builder.build()
    }
}
