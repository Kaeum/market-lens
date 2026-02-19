package io.kaeum.marketlens.infrastructure.redis

import io.kaeum.marketlens.domain.price.RealtimeTick
import io.kaeum.marketlens.domain.price.StockPriceSnapshot
import io.kaeum.marketlens.domain.price.TickType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.core.ReactiveHashOperations
import org.springframework.data.redis.core.script.RedisScript
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime

class SnapshotRedisBufferTest {

    private lateinit var redisTemplate: ReactiveRedisTemplate<String, String>
    private lateinit var hashOps: ReactiveHashOperations<String, String, String>
    private lateinit var buffer: SnapshotRedisBuffer

    @BeforeEach
    fun setUp() {
        redisTemplate = mockk(relaxed = true)
        hashOps = mockk(relaxed = true)
        every { redisTemplate.opsForHash<String, String>() } returns hashOps
        buffer = SnapshotRedisBuffer(redisTemplate)
    }

    @Test
    fun `updateIfNewer returns true when Lua script returns 1`() = runTest {
        val tick = createTick("005930", Instant.parse("2026-02-19T01:00:00Z"))
        every { redisTemplate.execute(any<RedisScript<Long>>(), any<List<String>>(), any<List<String>>()) } returns Flux.just(1L)

        val result = buffer.updateIfNewer(tick)

        assertTrue(result)
    }

    @Test
    fun `updateIfNewer returns false when Lua script returns 0 (older event)`() = runTest {
        val tick = createTick("005930", Instant.parse("2026-02-19T00:59:00Z"))
        every { redisTemplate.execute(any<RedisScript<Long>>(), any<List<String>>(), any<List<String>>()) } returns Flux.just(0L)

        val result = buffer.updateIfNewer(tick)

        assertFalse(result)
    }

    @Test
    fun `updateIfNewer returns false when Lua script returns empty`() = runTest {
        val tick = createTick("005930", Instant.parse("2026-02-19T01:00:00Z"))
        every { redisTemplate.execute(any<RedisScript<Long>>(), any<List<String>>(), any<List<String>>()) } returns Flux.empty()

        val result = buffer.updateIfNewer(tick)

        assertFalse(result)
    }

    @Test
    fun `getSnapshot returns null when Redis hash is empty`() = runTest {
        every { hashOps.entries("snapshot:005930") } returns Flux.empty()

        val result = buffer.getSnapshot("005930")

        assertNull(result)
    }

    @Test
    fun `getSnapshot returns StockPriceSnapshot from Redis hash fields`() = runTest {
        val entries = mapOf(
            "currentPrice" to "72000",
            "changeRate" to "1.50",
            "volume" to "10000000",
            "marketCap" to "430000000000000",
            "tradingValue" to "50000000000",
            "updatedAt" to "2026-02-19T01:00:00Z",
            "eventTime" to "2026-02-19T01:00:00Z",
        )
        every { hashOps.entries("snapshot:005930") } returns Flux.fromIterable(
            entries.map { (k, v) -> java.util.AbstractMap.SimpleEntry(k, v) as Map.Entry<String, String> }
        )

        val result = buffer.getSnapshot("005930")

        assertNotNull(result)
        assertEquals("005930", result!!.stockCode)
        assertEquals(72000L, result.currentPrice)
        assertEquals(BigDecimal("1.50"), result.changeRate)
        assertEquals(10000000L, result.volume)
        assertEquals(430000000000000L, result.marketCap)
        assertEquals(50000000000L, result.tradingValue)
    }

    @Test
    fun `getSnapshot returns snapshot without marketCap when field is absent`() = runTest {
        val entries = mapOf(
            "currentPrice" to "72000",
            "changeRate" to "1.50",
            "volume" to "10000000",
            "tradingValue" to "50000000000",
            "updatedAt" to "2026-02-19T01:00:00Z",
            "eventTime" to "2026-02-19T01:00:00Z",
        )
        every { hashOps.entries("snapshot:005930") } returns Flux.fromIterable(
            entries.map { (k, v) -> java.util.AbstractMap.SimpleEntry(k, v) as Map.Entry<String, String> }
        )

        val result = buffer.getSnapshot("005930")

        assertNotNull(result)
        assertNull(result!!.marketCap)
    }

    @Test
    fun `getSnapshots returns empty list for empty input`() = runTest {
        val result = buffer.getSnapshots(emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `drainDirtyCodes returns stock codes and clears dirty set`() = runTest {
        every { redisTemplate.execute(any<RedisScript<List<*>>>(), any<List<String>>(), any<List<String>>()) } returns Flux.just(listOf("005930", "000660"))

        val result = buffer.drainDirtyCodes()

        assertEquals(setOf("005930", "000660"), result)
    }

    @Test
    fun `drainDirtyCodes returns empty set when no dirty codes`() = runTest {
        every { redisTemplate.execute(any<RedisScript<List<*>>>(), any<List<String>>(), any<List<String>>()) } returns Flux.just(emptyList<String>())

        val result = buffer.drainDirtyCodes()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `warmUp writes all snapshots to Redis`() = runTest {
        val snapshots = listOf(
            StockPriceSnapshot("005930", 72000, BigDecimal("1.50"), 10000000, 430000000000000, 50000000000),
            StockPriceSnapshot("000660", 180000, BigDecimal("3.20"), 5000000, 130000000000000, 20000000000),
        )
        every { hashOps.putAll(any<String>(), any<Map<String, String>>()) } returns Mono.just(true)

        buffer.warmUp(snapshots)
    }

    private fun createTick(stockCode: String, eventTime: Instant) = RealtimeTick(
        stockCode = stockCode,
        currentPrice = 72000L,
        changeRate = BigDecimal("1.50"),
        volume = 500L,
        accumulatedVolume = 10_000_000L,
        tradingValue = 50_000_000_000L,
        tradeTime = LocalTime.of(10, 5, 30),
        eventTime = eventTime,
        tickType = TickType.TRADE,
    )
}
