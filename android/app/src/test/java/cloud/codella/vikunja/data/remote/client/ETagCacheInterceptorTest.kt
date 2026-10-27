package cloud.codella.vikunja.data.remote.client

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ETagCacheInterceptorTest {

    private lateinit var interceptor: ETagCacheInterceptor
    private lateinit var chain: Interceptor.Chain

    @Before
    fun setUp() {
        interceptor = ETagCacheInterceptor()
        chain = mockk()
    }

    @Test
    fun `GET request caches ETag from 200 response and attaches If-None-Match on subsequent request`() {
        val targetUrl = "https://tasks.codella.cloud/api/v1/tasks"
        val request1 = Request.Builder()
            .url(targetUrl)
            .get()
            .build()

        val response1 = Response.Builder()
            .request(request1)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("ETag", "W/\"etag-tasks-12345\"")
            .body("""[{"id":1,"title":"Test"}]""".toResponseBody("application/json".toMediaType()))
            .build()

        every { chain.request() } returns request1
        every { chain.proceed(any()) } returns response1

        val handledResponse1 = interceptor.intercept(chain)
        assertEquals(200, handledResponse1.code)

        // Second request to same URL
        val request2Slot = slot<Request>()
        val request2 = Request.Builder()
            .url(targetUrl)
            .get()
            .build()

        val response2 = Response.Builder()
            .request(request2)
            .protocol(Protocol.HTTP_1_1)
            .code(304)
            .message("Not Modified")
            .body("".toResponseBody("application/json".toMediaType()))
            .build()

        every { chain.request() } returns request2
        every { chain.proceed(capture(request2Slot)) } returns response2

        val handledResponse2 = interceptor.intercept(chain)

        // 1. Must attach If-None-Match with cached ETag
        assertEquals("W/\"etag-tasks-12345\"", request2Slot.captured.header("If-None-Match"))

        // 2. Must synthesize 304 into 200 with X-Vikunja-304 header and empty JSON array body
        assertEquals(200, handledResponse2.code)
        assertEquals("true", handledResponse2.header("X-Vikunja-304"))
        assertEquals("[]", handledResponse2.body?.string())
    }

    @Test
    fun `POST mutation invalidates cached ETags`() {
        val targetUrl = "https://tasks.codella.cloud/api/v1/tasks"
        val getRequest = Request.Builder().url(targetUrl).get().build()
        val getResponse = Response.Builder()
            .request(getRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("ETag", "W/\"etag-1\"")
            .body("[]".toResponseBody("application/json".toMediaType()))
            .build()

        every { chain.request() } returns getRequest
        every { chain.proceed(any()) } returns getResponse
        interceptor.intercept(chain)

        // Mutating POST request
        val postRequest = Request.Builder()
            .url(targetUrl)
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        val postResponse = Response.Builder()
            .request(postRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(201)
            .message("Created")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()

        every { chain.request() } returns postRequest
        every { chain.proceed(postRequest) } returns postResponse
        interceptor.intercept(chain)

        // Subsequent GET request should no longer send If-None-Match header
        val subsequentGetSlot = slot<Request>()
        every { chain.request() } returns getRequest
        every { chain.proceed(capture(subsequentGetSlot)) } returns getResponse
        interceptor.intercept(chain)

        assertNull("ETag header should have been invalidated by POST mutation", subsequentGetSlot.captured.header("If-None-Match"))
    }
}
