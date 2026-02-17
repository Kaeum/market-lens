package io.kaeum.marketlens.infrastructure.kis

import com.fasterxml.jackson.databind.ObjectMapper
import io.kaeum.marketlens.infrastructure.config.KisProperties
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class KisTokenManagerTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var tokenManager: KisTokenManager
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()

        val baseUrl = mockServer.url("/").toString().trimEnd('/')
        val properties = KisProperties(
            appKey = "test-key",
            appSecret = "test-secret",
            accountNumber = "test-account",
            baseUrl = baseUrl,
            tokenRefreshBeforeMinutes = 30,
        )
        val webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .build()

        tokenManager = KisTokenManager(webClient, properties)
    }

    @AfterEach
    fun tearDown() {
        tokenManager.destroy()
        mockServer.shutdown()
    }

    @Test
    fun `getAccessToken issues a new token on first call`() = runTest {
        enqueueTokenResponse("test-access-token", 86400)

        val token = tokenManager.getAccessToken()

        assertEquals("test-access-token", token)
        val request = mockServer.takeRequest()
        assertEquals("/oauth2/tokenP", request.path)
        assertEquals("POST", request.method)
    }

    @Test
    fun `getAccessToken returns cached token on subsequent calls`() = runTest {
        enqueueTokenResponse("test-access-token", 86400)

        val first = tokenManager.getAccessToken()
        val second = tokenManager.getAccessToken()

        assertEquals(first, second)
        // Only one HTTP request should be made
        assertEquals(1, mockServer.requestCount)
    }

    @Test
    fun `getAccessToken refreshes when token expires soon`() = runTest {
        // First token: expires_in = 1 second (within 30-min refresh window)
        enqueueTokenResponse("short-lived", 1)
        tokenManager.getAccessToken()

        // Second token for refresh
        enqueueTokenResponse("refreshed-token", 86400)
        val token = tokenManager.getAccessToken()

        assertEquals("refreshed-token", token)
        assertEquals(2, mockServer.requestCount)
    }

    @Test
    fun `token request body contains required fields`() = runTest {
        enqueueTokenResponse("token", 86400)

        tokenManager.getAccessToken()

        val request = mockServer.takeRequest()
        val body = objectMapper.readValue(request.body.readUtf8(), Map::class.java)
        assertEquals("client_credentials", body["grant_type"])
        assertEquals("test-key", body["appkey"])
        assertEquals("test-secret", body["appsecret"])
    }

    @Test
    fun `invalidateToken forces re-fetch on next getAccessToken call`() = runTest {
        enqueueTokenResponse("first-token", 86400)
        val first = tokenManager.getAccessToken()
        assertEquals("first-token", first)
        assertEquals(1, mockServer.requestCount)

        tokenManager.invalidateToken()

        enqueueTokenResponse("second-token", 86400)
        val second = tokenManager.getAccessToken()
        assertEquals("second-token", second)
        assertEquals(2, mockServer.requestCount)
    }

    private fun enqueueTokenResponse(accessToken: String, expiresIn: Long) {
        val responseBody = objectMapper.writeValueAsString(
            mapOf(
                "access_token" to accessToken,
                "token_type" to "Bearer",
                "expires_in" to expiresIn,
            )
        )
        mockServer.enqueue(
            MockResponse()
                .setBody(responseBody)
                .addHeader("Content-Type", "application/json")
        )
    }
}
