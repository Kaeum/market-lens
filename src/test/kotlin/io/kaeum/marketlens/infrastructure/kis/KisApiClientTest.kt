package io.kaeum.marketlens.infrastructure.kis

import com.fasterxml.jackson.databind.ObjectMapper
import io.kaeum.marketlens.global.exception.BusinessException
import io.kaeum.marketlens.infrastructure.config.KisProperties
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClient

class KisApiClientTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var tokenManager: KisTokenManager
    private lateinit var kisApiClient: KisApiClient
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

        tokenManager = mockk()
        kisApiClient = KisApiClient(webClient, properties, tokenManager)
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `getCurrentPrice returns valid snapshot for successful response`() = runTest {
        coEvery { tokenManager.getAccessToken() } returns "test-token"

        val responseBody = objectMapper.writeValueAsString(
            mapOf(
                "rt_cd" to "0",
                "msg_cd" to "MCA00000",
                "msg1" to "정상처리 되었습니다",
                "output" to mapOf(
                    "stck_prpr" to "72000",
                    "prdy_ctrt" to "1.50",
                    "acml_vol" to "10000000",
                    "hts_avls" to "430000000000000",
                    "acml_tr_pbmn" to "50000000000",
                ),
            )
        )
        mockServer.enqueue(
            MockResponse()
                .setBody(responseBody)
                .addHeader("Content-Type", "application/json")
        )

        val snapshot = kisApiClient.getCurrentPrice("005930")

        assertNotNull(snapshot)
        assertEquals("005930", snapshot.stockCode)
        assertEquals(72000L, snapshot.currentPrice)
        assertEquals("1.50".toBigDecimal(), snapshot.changeRate)
        assertEquals(10000000L, snapshot.volume)
        assertEquals(430000000000000L, snapshot.marketCap)
        assertEquals(50000000000L, snapshot.tradingValue)

        val request = mockServer.takeRequest()
        assertTrue(request.path!!.contains("FID_INPUT_ISCD=005930"))
        assertEquals("Bearer test-token", request.getHeader("authorization"))
        assertEquals("test-key", request.getHeader("appkey"))
        assertEquals("FHKST01010100", request.getHeader("tr_id"))
    }

    @Test
    fun `getCurrentPrice throws on KIS error response code`() = runTest {
        coEvery { tokenManager.getAccessToken() } returns "test-token"

        val responseBody = objectMapper.writeValueAsString(
            mapOf(
                "rt_cd" to "1",
                "msg_cd" to "EGW00000",
                "msg1" to "에러가 발생했습니다",
                "output" to null,
            )
        )
        // Enqueue for all 3 retry attempts
        repeat(3) {
            mockServer.enqueue(
                MockResponse()
                    .setBody(responseBody)
                    .addHeader("Content-Type", "application/json")
            )
        }

        assertThrows<BusinessException> {
            kisApiClient.getCurrentPrice("005930")
        }
    }

    @Test
    fun `getCurrentPrice throws on HTTP 500 error`() = runTest {
        coEvery { tokenManager.getAccessToken() } returns "test-token"

        // Enqueue 3 error responses (for all 3 retry attempts)
        repeat(3) {
            mockServer.enqueue(MockResponse().setResponseCode(500))
        }

        assertThrows<BusinessException> {
            kisApiClient.getCurrentPrice("005930")
        }
    }

    @Test
    fun `getLatestPrices returns empty list for empty input`() = runTest {
        val result = kisApiClient.getLatestPrices(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getLatestPrices returns snapshots for multiple stocks`() = runTest {
        coEvery { tokenManager.getAccessToken() } returns "test-token"

        listOf("005930" to "72000", "000660" to "180000").forEach { (_, price) ->
            val responseBody = objectMapper.writeValueAsString(
                mapOf(
                    "rt_cd" to "0",
                    "msg_cd" to "MCA00000",
                    "msg1" to "정상처리",
                    "output" to mapOf(
                        "stck_prpr" to price,
                        "prdy_ctrt" to "1.00",
                        "acml_vol" to "1000000",
                        "hts_avls" to "100000000000",
                        "acml_tr_pbmn" to "5000000000",
                    ),
                )
            )
            mockServer.enqueue(
                MockResponse()
                    .setBody(responseBody)
                    .addHeader("Content-Type", "application/json")
            )
        }

        val result = kisApiClient.getLatestPrices(listOf("005930", "000660"))

        assertEquals(2, result.size)
        assertEquals(72000L, result[0].currentPrice)
        assertEquals(180000L, result[1].currentPrice)
    }
}
