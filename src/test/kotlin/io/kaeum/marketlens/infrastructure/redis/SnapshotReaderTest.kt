package io.kaeum.marketlens.infrastructure.redis

import io.kaeum.marketlens.application.port.out.SnapshotCachePort
import io.kaeum.marketlens.domain.price.StockPriceSnapshot
import io.kaeum.marketlens.domain.price.StockPriceSnapshotRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SnapshotReaderTest {

    private val cachePort: SnapshotCachePort = mockk()
    private val repository: StockPriceSnapshotRepository = mockk()

    @Test
    fun `findByStockCodeIn returns from Redis when all codes hit`() = runTest {
        val reader = SnapshotReader(cachePort, repository)
        val codes = listOf("005930", "000660")
        val cached = listOf(
            StockPriceSnapshot("005930", 72000, BigDecimal("1.50"), 10000000, null),
            StockPriceSnapshot("000660", 180000, BigDecimal("3.20"), 5000000, null),
        )
        coEvery { cachePort.getSnapshots(codes) } returns cached

        val result = reader.findByStockCodeIn(codes)

        assertEquals(2, result.size)
        coVerify(exactly = 0) { repository.findByStockCodeIn(any()) }
    }

    @Test
    fun `findByStockCodeIn falls back to DB for cache misses`() = runTest {
        val reader = SnapshotReader(cachePort, repository)
        val codes = listOf("005930", "000660", "035720")
        val cached = listOf(
            StockPriceSnapshot("005930", 72000, BigDecimal("1.50"), 10000000, null),
        )
        val fromDb = listOf(
            StockPriceSnapshot("000660", 180000, BigDecimal("3.20"), 5000000, null),
            StockPriceSnapshot("035720", 95000, BigDecimal("2.10"), 3000000, null),
        )
        coEvery { cachePort.getSnapshots(codes) } returns cached
        coEvery { repository.findByStockCodeIn(listOf("000660", "035720")) } returns fromDb

        val result = reader.findByStockCodeIn(codes)

        assertEquals(3, result.size)
        assertTrue(result.any { it.stockCode == "005930" })
        assertTrue(result.any { it.stockCode == "000660" })
        assertTrue(result.any { it.stockCode == "035720" })
    }

    @Test
    fun `findByStockCodeIn uses DB only when cache port is null`() = runTest {
        val reader = SnapshotReader(null, repository)
        val codes = listOf("005930")
        val fromDb = listOf(StockPriceSnapshot("005930", 72000, BigDecimal("1.50"), 10000000, null))
        coEvery { repository.findByStockCodeIn(codes) } returns fromDb

        val result = reader.findByStockCodeIn(codes)

        assertEquals(1, result.size)
    }

    @Test
    fun `findByStockCodeIn returns empty for empty input`() = runTest {
        val reader = SnapshotReader(cachePort, repository)

        val result = reader.findByStockCodeIn(emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findById returns from Redis when hit`() = runTest {
        val reader = SnapshotReader(cachePort, repository)
        val snapshot = StockPriceSnapshot("005930", 72000, BigDecimal("1.50"), 10000000, null)
        coEvery { cachePort.getSnapshot("005930") } returns snapshot

        val result = reader.findById("005930")

        assertEquals("005930", result!!.stockCode)
        coVerify(exactly = 0) { repository.findById(any<String>()) }
    }

    @Test
    fun `findById falls back to DB when cache miss`() = runTest {
        val reader = SnapshotReader(cachePort, repository)
        val snapshot = StockPriceSnapshot("005930", 72000, BigDecimal("1.50"), 10000000, null)
        coEvery { cachePort.getSnapshot("005930") } returns null
        coEvery { repository.findById("005930") } returns snapshot

        val result = reader.findById("005930")

        assertEquals("005930", result!!.stockCode)
        coVerify(exactly = 1) { repository.findById("005930") }
    }

    @Test
    fun `findById uses DB only when cache port is null`() = runTest {
        val reader = SnapshotReader(null, repository)
        val snapshot = StockPriceSnapshot("005930", 72000, BigDecimal("1.50"), 10000000, null)
        coEvery { repository.findById("005930") } returns snapshot

        val result = reader.findById("005930")

        assertEquals("005930", result!!.stockCode)
    }
}
