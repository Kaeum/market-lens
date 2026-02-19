package io.kaeum.marketlens.infrastructure.redis

import io.kaeum.marketlens.application.port.out.SnapshotCachePort
import io.kaeum.marketlens.domain.price.StockPriceSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant

class SnapshotBatchFlusherTest {

    private lateinit var snapshotCachePort: SnapshotCachePort
    private lateinit var databaseClient: DatabaseClient
    private lateinit var flusher: SnapshotBatchFlusher

    @BeforeEach
    fun setUp() {
        snapshotCachePort = mockk()
        databaseClient = mockk()
        flusher = SnapshotBatchFlusher(snapshotCachePort, databaseClient)
    }

    @Test
    fun `flush does nothing when dirty set is empty`() = runTest {
        coEvery { snapshotCachePort.drainDirtyCodes() } returns emptySet()

        flusher.flush()

        coVerify(exactly = 0) { snapshotCachePort.getSnapshots(any()) }
    }

    @Test
    fun `flush upserts snapshots for dirty codes`() = runTest {
        val dirtyCodes = setOf("005930", "000660")
        val snapshots = listOf(
            StockPriceSnapshot("005930", 72000, BigDecimal("1.50"), 10000000, null, 50000000000, Instant.parse("2026-02-19T01:00:00Z")),
            StockPriceSnapshot("000660", 180000, BigDecimal("3.20"), 5000000, null, 20000000000, Instant.parse("2026-02-19T01:00:00Z")),
        )

        coEvery { snapshotCachePort.drainDirtyCodes() } returns dirtyCodes
        coEvery { snapshotCachePort.getSnapshots(dirtyCodes.toList()) } returns snapshots

        val executeSpec = mockk<GenericExecuteSpec>()
        every { databaseClient.sql(any<String>()) } returns executeSpec
        every { executeSpec.bind(any<String>(), any()) } returns executeSpec
        every { executeSpec.then() } returns Mono.empty()

        flusher.flush()

        coVerify(exactly = 1) { snapshotCachePort.drainDirtyCodes() }
        coVerify(exactly = 1) { snapshotCachePort.getSnapshots(dirtyCodes.toList()) }
    }

    @Test
    fun `flush skips when snapshots are empty despite dirty codes`() = runTest {
        coEvery { snapshotCachePort.drainDirtyCodes() } returns setOf("005930")
        coEvery { snapshotCachePort.getSnapshots(listOf("005930")) } returns emptyList()

        flusher.flush()
    }
}
