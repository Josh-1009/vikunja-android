package cloud.codella.vikunja.data.remote.client

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.ConcurrentHashMap

/**
 * ETag & Conditional HTTP Caching Interceptor.
 * Caches server ETags for GET requests to Vikunja list endpoints (/api/v1/projects, /api/v1/tasks).
 * Sends If-None-Match header when an ETag is cached.
 * On HTTP 304 (Not Modified), synthesizes an HTTP 200 response with an empty JSON array "[]"
 * and header "X-Vikunja-304: true", bypassing redundant deserialization and database writes.
 * Automatically invalidates cached ETags on mutating requests (POST, PUT, DELETE, PATCH).
 */
class ETagCacheInterceptor : Interceptor {

    private val etagMap = ConcurrentHashMap<String, String>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method = request.method
        val url = request.url
        val path = url.encodedPath

        // Invalidate cached ETags on mutating requests
        if (method != "GET") {
            if (path.contains("/api/v1/tasks") || path.contains("/api/v1/projects")) {
                etagMap.clear()
            }
            return chain.proceed(request)
        }

        // Only apply conditional ETag caching to list endpoints
        val isCacheableList = path.endsWith("/api/v1/tasks") ||
                path.endsWith("/api/v1/projects") ||
                path.matches(Regex(".*/api/v1/projects/\\d+/tasks$"))

        if (!isCacheableList) {
            return chain.proceed(request)
        }

        val cacheKey = url.toString()
        val cachedETag = etagMap[cacheKey]

        val networkRequest = if (!cachedETag.isNullOrBlank()) {
            request.newBuilder()
                .header("If-None-Match", cachedETag)
                .build()
        } else {
            request
        }

        val response = chain.proceed(networkRequest)

        // Capture ETag from successful response
        val responseEtag = response.header("ETag")
        if (response.isSuccessful && !responseEtag.isNullOrBlank()) {
            etagMap[cacheKey] = responseEtag
        }

        // Synthesize 304 response as HTTP 200 with empty array and marker header
        if (response.code == 304) {
            response.close()
            return response.newBuilder()
                .code(200)
                .message("OK (Not Modified)")
                .header("X-Vikunja-304", "true")
                .body("[]".toResponseBody("application/json".toMediaType()))
                .build()
        }

        return response
    }

    fun clearCache() {
        etagMap.clear()
    }
}
