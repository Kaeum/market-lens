package io.kaeum.marketlens.infrastructure.kis

import io.kaeum.marketlens.domain.price.RealtimeTick
import io.kaeum.marketlens.infrastructure.config.KisProperties
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KisWebSocketClientTest {

    private lateinit var kisProperties: KisProperties
    private lateinit var approvalKeyManager: KisApprovalKeyManager
    private lateinit var tickProducer: InMemoryTickProducer
    private lateinit var client: KisWebSocketClient

    @BeforeEach
    fun setUp() {
        kisProperties = KisProperties(
            appKey = "test-key",
            appSecret = "test-secret",
            wsUrl = "ws://localhost:9999",
            wsMaxSubscriptions = 3,
        )
        approvalKeyManager = mockk()
        tickProducer = mockk(relaxed = true)
        client = KisWebSocketClient(kisProperties, approvalKeyManager, tickProducer)
    }

    @Test
    fun `initial state is DISCONNECTED`() {
        assertEquals(KisWebSocketClient.ConnectionState.DISCONNECTED, client.getState())
    }

    @Test
    fun `subscribe respects max subscription limit`() = runTest {
        coEvery { approvalKeyManager.getApprovalKey() } returns "test-key"

        // Subscribe up to max (3)
        assertTrue(client.subscribe("005930"))
        assertTrue(client.subscribe("000660"))
        assertTrue(client.subscribe("035720"))

        // 4th should fail
        assertFalse(client.subscribe("051910"))

        assertEquals(3, client.getSubscribedStocks().size)
    }

    @Test
    fun `subscribe same stock twice is idempotent`() = runTest {
        coEvery { approvalKeyManager.getApprovalKey() } returns "test-key"

        assertTrue(client.subscribe("005930"))
        assertTrue(client.subscribe("005930"))

        assertEquals(1, client.getSubscribedStocks().size)
    }

    @Test
    fun `unsubscribe removes stock from subscriptions`() = runTest {
        coEvery { approvalKeyManager.getApprovalKey() } returns "test-key"

        client.subscribe("005930")
        client.subscribe("000660")
        assertEquals(2, client.getSubscribedStocks().size)

        client.unsubscribe("005930")
        assertEquals(1, client.getSubscribedStocks().size)
        assertFalse(client.getSubscribedStocks().contains("005930"))
    }

    @Test
    fun `unsubscribe non-existent stock is no-op`() = runTest {
        coEvery { approvalKeyManager.getApprovalKey() } returns "test-key"

        client.unsubscribe("005930") // Should not throw
        assertEquals(0, client.getSubscribedStocks().size)
    }

    @Test
    fun `disconnect clears subscriptions`() = runTest {
        coEvery { approvalKeyManager.getApprovalKey() } returns "test-key"

        client.subscribe("005930")
        client.subscribe("000660")

        client.disconnect()

        assertEquals(0, client.getSubscribedStocks().size)
        assertEquals(KisWebSocketClient.ConnectionState.DISCONNECTED, client.getState())
    }
}
