package cloud.codella.vikunja.data.remote.client

import cloud.codella.vikunja.core.datastore.PreferencesManager
import cloud.codella.vikunja.core.datastore.TokenManager
import cloud.codella.vikunja.data.remote.api.VikunjaRemoteApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class VikunjaNetworkClient(
    private val preferencesManager: PreferencesManager,
    private val tokenManager: TokenManager
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val dynamicBaseUrlInterceptor = DynamicBaseUrlInterceptor(preferencesManager)
    private val etagCacheInterceptor = ETagCacheInterceptor()
    private val authInterceptor = JwtAuthInterceptor(tokenManager)

    private val unauthenticatedClient: OkHttpClient by lazy {
        buildBaseOkHttpClient(allowSelfSigned = true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val tokenAuthenticator = TokenRefreshAuthenticator(
        tokenManager = tokenManager,
        preferencesManager = preferencesManager,
        json = json,
        unauthenticatedClientProvider = { unauthenticatedClient }
    )

    private fun buildBaseOkHttpClient(allowSelfSigned: Boolean): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        if (allowSelfSigned) {
            val (sslSocketFactory, trustManager) = TlsSocketFactoryProvider.createTrustAllSslSocketFactory()
            builder.sslSocketFactory(sslSocketFactory, trustManager)
            builder.hostnameVerifier { _, _ -> true }
        }

        return builder
    }

    private var cachedApi: VikunjaRemoteApi? = null
    private var lastConfiguredUrl: String = ""
    private var lastAllowSelfSigned: Boolean? = null

    fun getApi(): VikunjaRemoteApi {
        val currentServerUrl = runBlocking { preferencesManager.serverUrlFlow.first() }
            .ifBlank { "https://tasks.codella.cloud/" }
        val normalizedUrl = if (currentServerUrl.endsWith("/")) currentServerUrl else "$currentServerUrl/"
        val allowSelfSigned = runBlocking { preferencesManager.allowSelfSignedFlow.first() }

        synchronized(this) {
            val existing = cachedApi
            if (existing != null && lastConfiguredUrl == normalizedUrl && lastAllowSelfSigned == allowSelfSigned) {
                return existing
            }

            val okHttpClient = buildBaseOkHttpClient(allowSelfSigned)
                .addInterceptor(dynamicBaseUrlInterceptor)
                .addInterceptor(etagCacheInterceptor)
                .addInterceptor(authInterceptor)
                .authenticator(tokenAuthenticator)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()

            val api = retrofit.create(VikunjaRemoteApi::class.java)
            cachedApi = api
            lastConfiguredUrl = normalizedUrl
            lastAllowSelfSigned = allowSelfSigned
            return api
        }
    }

    fun invalidate() {
        synchronized(this) {
            cachedApi = null
            etagCacheInterceptor.clearCache()
        }
    }
}
