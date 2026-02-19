package io.kaeum.marketlens.infrastructure.redis

import io.kaeum.marketlens.application.port.out.SnapshotCachePort
import io.kaeum.marketlens.domain.price.StockPriceSnapshot
import io.kaeum.marketlens.domain.price.StockPriceSnapshotRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SnapshotRedisWarmUpTest {

    private val snapshotCachePort: SnapshotCachePort = mockk(relaxed = true)
    private val snapshotRepository: StockPriceSnapshotRepository = mockk()

    @Test
    fun `warmUp loads all snapshots from DB to Redis`() = runTest {
        val snapshots = listOf(
            StockPriceSnapshot("005930", 72000, BigDecimal("1.50"), 10000000, 430000000000000),
            StockPriceSnapshot("000660", 180000, BigDecimal("3.20"), 5000000, 130000000000000),
        )
        coEvery { snapshotRepository.findAll() } returns flowOf(*snapshots.toTypedArray())

        val warmUp = SnapshotRedisWarmUp(snapshotCachePort, snapshotRepository)
        warmUp.warmUp()

        coVerify { snapshotCachePort.warmUp(snapshots) }
    }

    @Test
    fun `warmUp handles empty DB gracefully`() = runTest {
        coEvery { snapshotRepository.findAll() } returns flowOf()

        val warmUp = SnapshotRedisWarmUp(snapshotCachePort, snapshotRepository)
        warmUp.warmUp()

        coVerify { snapshotCachePort.warmUp(emptyList()) }
    }
}
