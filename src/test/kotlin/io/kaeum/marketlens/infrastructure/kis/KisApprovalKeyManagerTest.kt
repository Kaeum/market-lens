package io.kaeum.marketlens.infrastructure.kis

import com.fasterxml.jackson.databind.ObjectMapper
import io.kaeum.marketlens.global.exception.BusinessException
import io.kaeum.marketlens.infrastructure.config.KisProperties
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClient

class KisApprovalKeyManagerTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var approvalKeyManager: KisApprovalKeyManager
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
        )
        val webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .build()

        approvalKeyManager = KisApprovalKeyManager(webClient, properties)
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `getApprovalKey fetches key on first call`() = runTest {
        enqueueApprovalKeyResponse("test-approval-key")

        val key = approvalKeyManager.getApprovalKey()

        assertEquals("test-approval-key", key)
        val request = mockServer.takeRequest()
        assertEquals("/oauth2/Approval", request.path)
        assertEquals("POST", request.method)
    }

    @Test
    fun `getApprovalKey returns cached key on subsequent calls`() = runTest {
        enqueueApprovalKeyResponse("test-approval-key")

        val first = approvalKeyManager.getApprovalKey()
        val second = approvalKeyManager.getApprovalKey()

        assertEquals(first, second)
        assertEquals(1, mockServer.requestCount)
    }

    @Test
    fun `invalidate forces re-fetch on next call`() = runTest {
        enqueueApprovalKeyResponse("first-key")
        val first = approvalKeyManager.getApprovalKey()
        assertEquals("first-key", first)
        assertEquals(1, mockServer.requestCount)

        approvalKeyManager.invalidate()

        enqueueApprovalKeyResponse("second-key")
        val second = approvalKeyManager.getApprovalKey()
        assertEquals("second-key", second)
        assertEquals(2, mockServer.requestCount)
    }

    @Test
    fun `request body contains required fields`() = runTest {
        enqueueApprovalKeyResponse("key")

        approvalKeyManager.getApprovalKey()

        val request = mockServer.takeRequest()
        val body = objectMapper.readValue(request.body.readUtf8(), Map::class.java)
        assertEquals("client_credentials", body["grant_type"])
        assertEquals("test-key", body["appkey"])
        assertEquals("test-secret", body["secretkey"])
    }

    @Test
    fun `throws BusinessException on server error`() = runTest {
        // Enqueue for all 3 retry attempts
        repeat(3) {
            mockServer.enqueue(MockResponse().setResponseCode(500))
        }

        assertThrows<BusinessException> {
            approvalKeyManager.getApprovalKey()
        }
    }

    private fun enqueueApprovalKeyResponse(approvalKey: String) {
        val responseBody = objectMapper.writeValueAsString(
            mapOf("approval_key" to approvalKey)
        )
        mockServer.enqueue(
            MockResponse()
                .setBody(responseBody)
                .addHeader("Content-Type", "application/json")
        )
    }
}
