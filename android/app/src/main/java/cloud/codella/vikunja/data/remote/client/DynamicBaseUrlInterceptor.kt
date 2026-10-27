package cloud.codella.vikunja.data.remote.client

import cloud.codella.vikunja.core.datastore.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

class DynamicBaseUrlInterceptor(
    private val preferencesManager: PreferencesManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val currentBaseUrl = runBlocking {
            preferencesManager.serverUrlFlow.first()
        }

        if (currentBaseUrl.isNotBlank()) {
            val targetHttpUrl = currentBaseUrl.toHttpUrlOrNull()
            if (targetHttpUrl != null) {
                val newUrl = request.url.newBuilder()
                    .scheme(targetHttpUrl.scheme)
                    .host(targetHttpUrl.host)
                    .port(targetHttpUrl.port)
                    .build()
                request = request.newBuilder().url(newUrl).build()
            }
        }
        return chain.proceed(request)
    }
}
