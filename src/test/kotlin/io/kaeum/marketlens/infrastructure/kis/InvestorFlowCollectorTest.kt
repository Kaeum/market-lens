package io.kaeum.marketlens.infrastructure.kis

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.DayOfWeek
import java.time.LocalDate

class InvestorFlowCollectorTest {

    private lateinit var kisApiClient: KisApiClient
    private lateinit var databaseClient: DatabaseClient
    private lateinit var collector: InvestorFlowCollector

    @BeforeEach
    fun setUp() {
        kisApiClient = mockk()
        databaseClient = mockk()
        collector = InvestorFlowCollector(kisApiClient, databaseClient)
    }

    @Test
    fun `runPollingCycle skips when no active stocks found`() = runTest {
        val executeSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        every { databaseClient.sql(any<String>()) } returns executeSpec
        every { executeSpec.map(any<java.util.function.BiFunction<io.r2dbc.spi.Row, io.r2dbc.spi.RowMetadata, String>>()) } returns mockk {
            every { all() } returns Flux.empty()
        }

        collector.runPollingCycle()

        coVerify(exactly = 0) { kisApiClient.fetchInvestorFlow(any(), any(), any()) }
    }

    @Test
    fun `runPollingCycle collects and upserts investor flow data`() = runTest {
        // Mock fetchActiveThemeStockCodes
        val selectSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        every { databaseClient.sql(match<String> { it.contains("theme_stock") }) } returns selectSpec
        every { selectSpec.map(any<java.util.function.BiFunction<io.r2dbc.spi.Row, io.r2dbc.spi.RowMetadata, String>>()) } returns mockk {
            every { all() } returns Flux.just("005930", "000660")
        }

        // Mock KIS API responses
        val flowItems = listOf(
            KisApiClient.InvestorFlowItem(
                investorName = "개인",
                sellVolume = 1000, buyVolume = 2000, netVolume = 1000,
                sellAmount = 100000, buyAmount = 200000, netAmount = 100000,
            ),
            KisApiClient.InvestorFlowItem(
                investorName = "외국인",
                sellVolume = 3000, buyVolume = 1000, netVolume = -2000,
                sellAmount = 300000, buyAmount = 100000, netAmount = -200000,
            ),
        )
        coEvery { kisApiClient.fetchInvestorFlow(any(), any(), any()) } returns flowItems

        // Mock batch upsert
        val upsertSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        every { databaseClient.sql(match<String> { it.contains("INSERT INTO investor_flow") }) } returns upsertSpec
        every { upsertSpec.bind(any<String>(), any()) } returns upsertSpec
        every { upsertSpec.fetch() } returns mockk {
            every { rowsUpdated() } returns Mono.just(4L)
        }

        collector.runPollingCycle()

        coVerify(exactly = 2) { kisApiClient.fetchInvestorFlow(any(), any(), any()) }
    }

    @Test
    fun `runPollingCycle continues when individual stock fetch fails`() = runTest {
        // Mock fetchActiveThemeStockCodes
        val selectSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        every { databaseClient.sql(match<String> { it.contains("theme_stock") }) } returns selectSpec
        every { selectSpec.map(any<java.util.function.BiFunction<io.r2dbc.spi.Row, io.r2dbc.spi.RowMetadata, String>>()) } returns mockk {
            every { all() } returns Flux.just("005930", "000660", "035720")
        }

        // First stock fails, others succeed
        coEvery { kisApiClient.fetchInvestorFlow("005930", any(), any()) } throws RuntimeException("API error")
        coEvery { kisApiClient.fetchInvestorFlow("000660", any(), any()) } returns listOf(
            KisApiClient.InvestorFlowItem("개인", 1000, 2000, 1000, 100000, 200000, 100000),
        )
        coEvery { kisApiClient.fetchInvestorFlow("035720", any(), any()) } returns listOf(
            KisApiClient.InvestorFlowItem("외국인", 500, 800, 300, 50000, 80000, 30000),
        )

        // Mock batch upsert
        val upsertSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        every { databaseClient.sql(match<String> { it.contains("INSERT INTO investor_flow") }) } returns upsertSpec
        every { upsertSpec.bind(any<String>(), any()) } returns upsertSpec
        every { upsertSpec.fetch() } returns mockk {
            every { rowsUpdated() } returns Mono.just(2L)
        }

        collector.runPollingCycle()

        // All 3 stocks attempted
        coVerify(exactly = 3) { kisApiClient.fetchInvestorFlow(any(), any(), any()) }
    }

    @Test
    fun `getPreviousTradingDay skips weekends`() {
        // Monday 2026-02-16 → 5 trading days back should skip weekends
        val monday = LocalDate.of(2026, 2, 16)
        val result = collector.getPreviousTradingDay(monday, 5)

        // 5 trading days back from Monday Feb 16:
        // Feb 13 (Fri) = 1, Feb 12 (Thu) = 2, Feb 11 (Wed) = 3, Feb 10 (Tue) = 4, Feb 9 (Mon) = 5
        assertEquals(LocalDate.of(2026, 2, 9), result)
        assertEquals(DayOfWeek.MONDAY, result.dayOfWeek)
    }

    @Test
    fun `getPreviousTradingDay from Wednesday`() {
        val wednesday = LocalDate.of(2026, 2, 18)
        val result = collector.getPreviousTradingDay(wednesday, 3)

        // 3 trading days back from Wed Feb 18:
        // Feb 17 (Tue) = 1, Feb 16 (Mon) = 2, Feb 13 (Fri) = 3
        assertEquals(LocalDate.of(2026, 2, 13), result)
        assertEquals(DayOfWeek.FRIDAY, result.dayOfWeek)
    }
}
