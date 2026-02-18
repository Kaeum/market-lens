package io.kaeum.marketlens.infrastructure.kis

import io.kaeum.marketlens.domain.price.RealtimeTick
import io.kaeum.marketlens.domain.price.TickType
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class InMemoryTickProducerTest {

    private val producer = InMemoryTickProducer()

    @Test
    fun `emit publishes tick to subscribers`() = runTest(UnconfinedTestDispatcher()) {
        val tick = createTick("005930", 72000L)

        val deferred = async {
            producer.ticks.first()
        }

        producer.emit(tick)
        val received = deferred.await()

        assertEquals("005930", received.stockCode)
        assertEquals(72000L, received.currentPrice)
    }

    @Test
    fun `emit multiple ticks are received in order`() = runTest(UnconfinedTestDispatcher()) {
        val ticks = listOf(
            createTick("005930", 72000L),
            createTick("000660", 180000L),
            createTick("005930", 72500L),
        )

        val deferred = async {
            producer.ticks.take(3).toList()
        }

        ticks.forEach { producer.emit(it) }
        val received = deferred.await()

        assertEquals(3, received.size)
        assertEquals(72000L, received[0].currentPrice)
        assertEquals(180000L, received[1].currentPrice)
        assertEquals(72500L, received[2].currentPrice)
    }

    private fun createTick(stockCode: String, price: Long) = RealtimeTick(
        stockCode = stockCode,
        currentPrice = price,
        changeRate = BigDecimal("1.50"),
        volume = 500,
        accumulatedVolume = 10_000_000,
        tradingValue = 50_000_000_000,
        tradeTime = LocalTime.of(10, 5, 30),
        eventTime = Instant.now(),
        tickType = TickType.TRADE,
    )
}
