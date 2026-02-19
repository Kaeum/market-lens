package io.kaeum.marketlens.infrastructure.krx

import io.kaeum.marketlens.domain.correlation.StockCorrelation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.LocalDate

class CorrelationCalculatorTest {

    private lateinit var databaseClient: DatabaseClient
    private lateinit var calculator: CorrelationCalculator

    @BeforeEach
    fun setUp() {
        databaseClient = mockk()
        calculator = CorrelationCalculator(databaseClient)
    }

    @Test
    fun `computePearson returns 1 for perfectly correlated series`() {
        val xs = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val ys = listOf(2.0, 4.0, 6.0, 8.0, 10.0)

        val result = calculator.computePearson(xs, ys)

        assertEquals(1.0, result!!, 0.0001)
    }

    @Test
    fun `computePearson returns -1 for perfectly inversely correlated series`() {
        val xs = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val ys = listOf(10.0, 8.0, 6.0, 4.0, 2.0)

        val result = calculator.computePearson(xs, ys)

        assertEquals(-1.0, result!!, 0.0001)
    }

    @Test
    fun `computePearson returns near 0 for uncorrelated series`() {
        val xs = listOf(1.0, -1.0, 1.0, -1.0, 1.0, -1.0)
        val ys = listOf(1.0, 1.0, -1.0, -1.0, 1.0, 1.0)

        val result = calculator.computePearson(xs, ys)

        assertTrue(result!! < 0.3 && result > -0.3, "Expected near-zero correlation, got $result")
    }

    @Test
    fun `computePearson returns null for constant series`() {
        val xs = listOf(5.0, 5.0, 5.0, 5.0)
        val ys = listOf(1.0, 2.0, 3.0, 4.0)

        val result = calculator.computePearson(xs, ys)

        assertNull(result)
    }

    @Test
    fun `computePearson returns null for empty series`() {
        val result = calculator.computePearson(emptyList(), emptyList())

        assertNull(result)
    }

    @Test
    fun `computeCoRiseRate calculates correctly`() {
        val xs = listOf(1.0, -1.0, 2.0, -0.5, 3.0)
        val ys = listOf(0.5, 1.0, 1.5, -1.0, 2.0)
        // Both positive: index 0 (1.0, 0.5), index 2 (2.0, 1.5), index 4 (3.0, 2.0) = 3/5

        val result = calculator.computeCoRiseRate(xs, ys)

        assertEquals(0.6, result, 0.0001)
    }

    @Test
    fun `computeCoRiseRate returns 0 for empty series`() {
        val result = calculator.computeCoRiseRate(emptyList(), emptyList())

        assertEquals(0.0, result, 0.0001)
    }

    @Test
    fun `computeCoRiseRate returns 1 when all days both rise`() {
        val xs = listOf(1.0, 2.0, 3.0)
        val ys = listOf(0.5, 1.5, 2.5)

        val result = calculator.computeCoRiseRate(xs, ys)

        assertEquals(1.0, result, 0.0001)
    }

    @Test
    fun `enumerateThemePairs generates correct pairs with canonical order`() {
        val themeGroups = mapOf(
            1L to listOf("005930", "000660", "035720"),
            2L to listOf("005930", "051910"),
        )

        val pairs = calculator.enumerateThemePairs(themeGroups)

        // Theme 1: (000660, 005930), (000660, 035720), (005930, 035720)
        // Theme 2: (005930, 051910)
        assertEquals(4, pairs.size)
        assertTrue(pairs.contains(Pair("000660", "005930")))
        assertTrue(pairs.contains(Pair("000660", "035720")))
        assertTrue(pairs.contains(Pair("005930", "035720")))
        assertTrue(pairs.contains(Pair("005930", "051910")))
        // Verify canonical order: first < second
        for ((a, b) in pairs) {
            assertTrue(a < b, "Expected $a < $b")
        }
    }

    @Test
    fun `enumerateThemePairs deduplicates cross-theme pairs`() {
        val themeGroups = mapOf(
            1L to listOf("005930", "000660"),
            2L to listOf("000660", "005930"),
        )

        val pairs = calculator.enumerateThemePairs(themeGroups)

        // Same pair from two themes → deduplicated
        assertEquals(1, pairs.size)
        assertTrue(pairs.contains(Pair("000660", "005930")))
    }

    @Test
    fun `alignByDate returns intersection of dates`() {
        val returnsA = listOf(
            Pair(LocalDate.of(2026, 1, 1), 1.0),
            Pair(LocalDate.of(2026, 1, 2), 2.0),
            Pair(LocalDate.of(2026, 1, 3), 3.0),
        )
        val returnsB = listOf(
            Pair(LocalDate.of(2026, 1, 2), 4.0),
            Pair(LocalDate.of(2026, 1, 3), 5.0),
            Pair(LocalDate.of(2026, 1, 4), 6.0),
        )

        val (alignedA, alignedB) = calculator.alignByDate(returnsA, returnsB)

        assertEquals(listOf(2.0, 3.0), alignedA)
        assertEquals(listOf(4.0, 5.0), alignedB)
    }

    @Test
    fun `runDailyBatch skips when no theme stock groups`() = runTest {
        mockThemeStockGroupsQuery(emptyList())

        calculator.runDailyBatch()

        // No daily returns query should be made
        coVerify(exactly = 1) { databaseClient.sql(match<String> { it.contains("theme_stock") }) }
    }

    @Test
    fun `runDailyBatch skips when no daily return data`() = runTest {
        mockThemeStockGroupsQuery(
            listOf(Pair(1L, "005930"), Pair(1L, "000660")),
        )
        mockDailyReturnsQuery(emptyList())

        calculator.runDailyBatch()
    }

    @Test
    fun `runDailyBatch skips pair when overlap below MIN_OVERLAP`() = runTest {
        mockThemeStockGroupsQuery(
            listOf(Pair(1L, "005930"), Pair(1L, "000660")),
        )

        // Only 5 overlapping days — below MIN_OVERLAP (20)
        val returns = (1..5).flatMap { day ->
            val date = LocalDate.of(2026, 1, day)
            listOf(
                Triple("005930", date, day.toDouble()),
                Triple("000660", date, day * 2.0),
            )
        }
        mockDailyReturnsQuery(returns)

        calculator.runDailyBatch()

        // No upsert should happen — insufficient overlap
        coVerify(exactly = 0) { databaseClient.sql(match<String> { it.contains("INSERT INTO stock_correlation") }) }
    }

    @Test
    fun `runDailyBatch computes and upserts correlations`() = runTest {
        mockThemeStockGroupsQuery(
            listOf(Pair(1L, "005930"), Pair(1L, "000660")),
        )

        // 25 overlapping days — above MIN_OVERLAP
        val returns = (1..25).flatMap { day ->
            val date = LocalDate.of(2026, 1, day)
            listOf(
                Triple("005930", date, day.toDouble()),
                Triple("000660", date, day * 2.0),
            )
        }
        mockDailyReturnsQuery(returns)

        val executeSpec = mockk<DatabaseClient.GenericExecuteSpec>(relaxed = true)
        every { databaseClient.sql(match<String> { it.contains("INSERT INTO stock_correlation") }) } returns executeSpec
        every { executeSpec.bind(any<String>(), any()) } returns executeSpec
        every { executeSpec.fetch() } returns mockk {
            every { rowsUpdated() } returns Mono.just(1L)
        }

        calculator.runDailyBatch()

        coVerify(exactly = 1) { databaseClient.sql(match<String> { it.contains("INSERT INTO stock_correlation") }) }
    }

    private fun mockThemeStockGroupsQuery(rows: List<Pair<Long, String>>) {
        val executeSpec = mockk<DatabaseClient.GenericExecuteSpec>()
        every { databaseClient.sql(match<String> { it.contains("theme_stock") }) } returns executeSpec
        every { executeSpec.map(any<java.util.function.BiFunction<io.r2dbc.spi.Row, io.r2dbc.spi.RowMetadata, Any>>()) } answers {
            val mapper = firstArg<java.util.function.BiFunction<io.r2dbc.spi.Row, io.r2dbc.spi.RowMetadata, Any>>()
            val mappedRows = rows.map { (themeId, stockCode) ->
                val row = mockk<io.r2dbc.spi.Row>()
                every { row.get("theme_id", java.lang.Long::class.java) } returns themeId as java.lang.Long
                every { row.get("stock_code", String::class.java) } returns stockCode
                mapper.apply(row, mockk())
            }
            mockk {
                every { all() } returns Flux.fromIterable(mappedRows)
            }
        }
    }

    private fun mockDailyReturnsQuery(rows: List<Triple<String, LocalDate, Double>>) {
        val executeSpec = mockk<DatabaseClient.GenericExecuteSpec>()
        every { databaseClient.sql(match<String> { it.contains("stock_price_daily") }) } returns executeSpec
        every { executeSpec.map(any<java.util.function.BiFunction<io.r2dbc.spi.Row, io.r2dbc.spi.RowMetadata, Any>>()) } answers {
            val mapper = firstArg<java.util.function.BiFunction<io.r2dbc.spi.Row, io.r2dbc.spi.RowMetadata, Any>>()
            val mappedRows = rows.map { (stockCode, date, changeRate) ->
                val row = mockk<io.r2dbc.spi.Row>()
                every { row.get("stock_code", String::class.java) } returns stockCode
                every { row.get("trading_date", LocalDate::class.java) } returns date
                every { row.get("change_rate", BigDecimal::class.java) } returns BigDecimal.valueOf(changeRate)
                mapper.apply(row, mockk())
            }
            mockk {
                every { all() } returns Flux.fromIterable(mappedRows)
            }
        }
    }
}
