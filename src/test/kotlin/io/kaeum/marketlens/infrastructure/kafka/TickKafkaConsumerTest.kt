package io.kaeum.marketlens.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kaeum.marketlens.domain.price.RealtimeTick
import io.kaeum.marketlens.domain.price.TickType
import io.kaeum.marketlens.infrastructure.kis.InMemoryTickProducer
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TickKafkaConsumerTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val inMemoryTickProducer = InMemoryTickProducer()

    @Test
    fun `deserialize valid JSON to RealtimeTick`() {
        val tick = createTick("005930", 72000L)
        val json = objectMapper.writeValueAsString(tick)
        val deserialized = objectMapper.readValue(json, RealtimeTick::class.java)

        assertEquals("005930", deserialized.stockCode)
        assertEquals(72000L, deserialized.currentPrice)
        assertEquals(BigDecimal("1.50"), deserialized.changeRate)
        assertEquals(500L, deserialized.volume)
        assertEquals(10_000_000L, deserialized.accumulatedVolume)
        assertEquals(50_000_000_000L, deserialized.tradingValue)
        assertEquals(TickType.TRADE, deserialized.tickType)
    }

    @Test
    fun `deserialized tick emitted to InMemoryTickProducer is received by subscribers`() =
        runTest(UnconfinedTestDispatcher()) {
            val tick = createTick("000660", 180000L)

            val deferred = async {
                inMemoryTickProducer.ticks.first()
            }

            inMemoryTickProducer.emit(tick)
            val received = deferred.await()

            assertEquals("000660", received.stockCode)
            assertEquals(180000L, received.currentPrice)
        }

    @Test
    fun `invalid JSON does not crash — returns null on readValue`() {
        val invalidJson = "{invalid json}"
        val result = runCatching {
            objectMapper.readValue(invalidJson, RealtimeTick::class.java)
        }
        assertEquals(true, result.isFailure)
    }

    @Test
    fun `roundtrip serialization preserves all fields`() {
        val original = createTick("035720", 95000L)
        val json = objectMapper.writeValueAsString(original)
        val restored = objectMapper.readValue(json, RealtimeTick::class.java)

        assertEquals(original, restored)
    }

    private fun createTick(stockCode: String, price: Long) = RealtimeTick(
        stockCode = stockCode,
        currentPrice = price,
        changeRate = BigDecimal("1.50"),
        volume = 500,
        accumulatedVolume = 10_000_000,
        tradingValue = 50_000_000_000,
        tradeTime = LocalTime.of(10, 5, 30),
        eventTime = Instant.parse("2026-02-18T01:05:30Z"),
        tickType = TickType.TRADE,
    )
}
