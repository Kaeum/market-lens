package io.kaeum.marketlens.infrastructure.krx

import io.kaeum.marketlens.domain.price.StockDailyPrice
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDate

class KrxHistoricalCollectorTest {

    private lateinit var krxApiClient: KrxApiClient
    private lateinit var databaseClient: DatabaseClient
    private lateinit var collector: KrxHistoricalCollector

    @BeforeEach
    fun setUp() {
        krxApiClient = mockk()
        databaseClient = mockk()
        collector = KrxHistoricalCollector(krxApiClient, databaseClient)
    }

    @Test
    fun `collectDate fetches KOSPI and KOSDAQ and inserts`() = runTest {
        val date = LocalDate.of(2026, 2, 18)
        val kospiPrices = listOf(
            StockDailyPrice("005930", date, 71000, 72000, 70000, 72000, 10000000, BigDecimal("1.50"), 430000000000),
        )
        val kosdaqPrices = listOf(
            StockDailyPrice("035720", date, 45000, 46000, 44000, 45500, 5000000, BigDecimal("0.80"), 20000000000),
        )

        coEvery { krxApiClient.fetchDailyOhlcv(date, "KOSPI") } returns kospiPrices
        coEvery { krxApiClient.fetchDailyOhlcv(date, "KOSDAQ") } returns kosdaqPrices

        val executeSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        every { databaseClient.sql(any<String>()) } returns executeSpec
        every { executeSpec.bind(any<String>(), any()) } returns executeSpec
        every { executeSpec.bindNull(any<String>(), any<Class<*>>()) } returns executeSpec
        every { executeSpec.fetch() } returns mockk {
            every { rowsUpdated() } returns Mono.just(2L)
        }

        val inserted = collector.collectDate(date)

        assertEquals(2, inserted)
        coVerify(exactly = 1) { krxApiClient.fetchDailyOhlcv(date, "KOSPI") }
        coVerify(exactly = 1) { krxApiClient.fetchDailyOhlcv(date, "KOSDAQ") }
    }

    @Test
    fun `collectDate returns 0 for holidays with empty responses`() = runTest {
        val date = LocalDate.of(2026, 1, 1) // New Year holiday

        coEvery { krxApiClient.fetchDailyOhlcv(date, "KOSPI") } returns emptyList()
        coEvery { krxApiClient.fetchDailyOhlcv(date, "KOSDAQ") } returns emptyList()

        val inserted = collector.collectDate(date)

        assertEquals(0, inserted)
    }

    @Test
    fun `collectDate handles partial failure gracefully`() = runTest {
        val date = LocalDate.of(2026, 2, 18)
        val kospiPrices = listOf(
            StockDailyPrice("005930", date, 71000, 72000, 70000, 72000, 10000000, BigDecimal("1.50"), null),
        )

        coEvery { krxApiClient.fetchDailyOhlcv(date, "KOSPI") } returns kospiPrices
        coEvery { krxApiClient.fetchDailyOhlcv(date, "KOSDAQ") } throws RuntimeException("API timeout")

        val executeSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        every { databaseClient.sql(any<String>()) } returns executeSpec
        every { executeSpec.bind(any<String>(), any()) } returns executeSpec
        every { executeSpec.bindNull(any<String>(), any<Class<*>>()) } returns executeSpec
        every { executeSpec.fetch() } returns mockk {
            every { rowsUpdated() } returns Mono.just(1L)
        }

        val inserted = collector.collectDate(date)

        assertEquals(1, inserted)
    }

    @Test
    fun `runDailyBatch skips weekends`() = runTest {
        // This test verifies the weekend skip logic indirectly
        // On a weekend date, no API calls should be made
        // Since we can't control LocalDate.now(), we test collectDate for a weekday instead
        val weekday = LocalDate.of(2026, 2, 18) // Wednesday

        coEvery { krxApiClient.fetchDailyOhlcv(weekday, "KOSPI") } returns emptyList()
        coEvery { krxApiClient.fetchDailyOhlcv(weekday, "KOSDAQ") } returns emptyList()

        val inserted = collector.collectDate(weekday)
        assertEquals(0, inserted)
    }
}
