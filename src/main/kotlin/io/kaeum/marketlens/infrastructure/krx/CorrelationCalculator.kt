package io.kaeum.marketlens.infrastructure.krx

import io.kaeum.marketlens.domain.correlation.StockCorrelation
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.sqrt

@Component
@Profile("!test")
class CorrelationCalculator(
    private val databaseClient: DatabaseClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @PostConstruct
    fun init() {
        scope.launch {
            try {
                val count = countCorrelationRows()
                if (count == 0L) {
                    log.info("stock_correlation table is empty, starting bootstrap calculation")
                    recalculateAll()
                } else {
                    log.info("stock_correlation table has {} rows, skipping bootstrap", count)
                }
            } catch (e: Exception) {
                log.error("Correlation bootstrap failed: {}", e.message, e)
            }
        }
    }

    private suspend fun countCorrelationRows(): Long {
        return databaseClient.sql("SELECT count(*) AS cnt FROM stock_correlation")
            .map { row, _ -> row.get("cnt", java.lang.Long::class.java)?.toLong() ?: 0L }
            .first()
            .awaitFirstOrNull() ?: 0L
    }

    suspend fun recalculateAll() {
        log.info("Starting full correlation recalculation (window={}days)", WINDOW_DAYS)
        runDailyBatch()
    }

    suspend fun runDailyBatch() {
        val themeGroups = fetchThemeStockGroups()
        if (themeGroups.isEmpty()) {
            log.info("No theme-stock groups found, skipping correlation calculation")
            return
        }

        val dailyReturns = fetchDailyReturns()
        if (dailyReturns.isEmpty()) {
            log.info("No daily return data available, skipping correlation calculation")
            return
        }

        val pairs = enumerateThemePairs(themeGroups)
        log.info("Calculating correlations for {} pairs from {} themes", pairs.size, themeGroups.size)

        val correlations = mutableListOf<StockCorrelation>()
        for ((codeA, codeB) in pairs) {
            val returnsA = dailyReturns[codeA] ?: continue
            val returnsB = dailyReturns[codeB] ?: continue

            val (alignedA, alignedB) = alignByDate(returnsA, returnsB)
            if (alignedA.size < MIN_OVERLAP) continue

            val pearson = computePearson(alignedA, alignedB) ?: continue
            val coRise = computeCoRiseRate(alignedA, alignedB)

            correlations.add(
                StockCorrelation(
                    stockCodeA = minOf(codeA, codeB),
                    stockCodeB = maxOf(codeA, codeB),
                    correlation = BigDecimal.valueOf(pearson).setScale(4, RoundingMode.HALF_UP),
                    coRiseRate = BigDecimal.valueOf(coRise).setScale(4, RoundingMode.HALF_UP),
                    windowDays = WINDOW_DAYS,
                )
            )
        }

        if (correlations.isNotEmpty()) {
            batchUpsert(correlations)
            log.info("Correlation calculation completed: {} pairs upserted", correlations.size)
        } else {
            log.info("No valid correlation pairs computed (insufficient overlap or constant prices)")
        }
    }

    internal suspend fun fetchDailyReturns(): Map<String, List<Pair<LocalDate, Double>>> {
        val rows = databaseClient.sql(
            """
            SELECT stock_code, bucket::date AS trading_date, change_rate
            FROM stock_price_daily
            WHERE bucket >= NOW() - INTERVAL '$WINDOW_DAYS days'
              AND change_rate IS NOT NULL
            ORDER BY stock_code, trading_date
            """.trimIndent()
        )
            .map { row, _ ->
                Triple(
                    row.get("stock_code", String::class.java)!!,
                    row.get("trading_date", LocalDate::class.java)!!,
                    row.get("change_rate", BigDecimal::class.java)!!.toDouble(),
                )
            }
            .all()
            .collectList()
            .awaitSingle()

        return rows.groupBy(
            keySelector = { it.first },
            valueTransform = { Pair(it.second, it.third) },
        )
    }

    internal suspend fun fetchThemeStockGroups(): Map<Long, List<String>> {
        val rows = databaseClient.sql(
            """
            SELECT theme_id, stock_code FROM theme_stock ORDER BY theme_id
            """.trimIndent()
        )
            .map { row, _ ->
                Pair(
                    row.get("theme_id", java.lang.Long::class.java)!!.toLong(),
                    row.get("stock_code", String::class.java)!!,
                )
            }
            .all()
            .collectList()
            .awaitSingle()

        return rows.groupBy(
            keySelector = { it.first },
            valueTransform = { it.second },
        )
    }

    internal fun enumerateThemePairs(themeGroups: Map<Long, List<String>>): Set<Pair<String, String>> {
        val pairs = mutableSetOf<Pair<String, String>>()
        for ((_, stocks) in themeGroups) {
            for (i in stocks.indices) {
                for (j in i + 1 until stocks.size) {
                    val a = minOf(stocks[i], stocks[j])
                    val b = maxOf(stocks[i], stocks[j])
                    pairs.add(Pair(a, b))
                }
            }
        }
        return pairs
    }

    internal fun alignByDate(
        returnsA: List<Pair<LocalDate, Double>>,
        returnsB: List<Pair<LocalDate, Double>>,
    ): Pair<List<Double>, List<Double>> {
        val mapB = returnsB.associate { it.first to it.second }
        val alignedA = mutableListOf<Double>()
        val alignedB = mutableListOf<Double>()
        for ((date, valueA) in returnsA) {
            val valueB = mapB[date] ?: continue
            alignedA.add(valueA)
            alignedB.add(valueB)
        }
        return Pair(alignedA, alignedB)
    }

    internal fun computePearson(xs: List<Double>, ys: List<Double>): Double? {
        val n = xs.size
        if (n == 0) return null

        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumX2 = 0.0
        var sumY2 = 0.0

        for (i in 0 until n) {
            sumX += xs[i]
            sumY += ys[i]
            sumXY += xs[i] * ys[i]
            sumX2 += xs[i] * xs[i]
            sumY2 += ys[i] * ys[i]
        }

        val denominator = sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY))
        if (denominator == 0.0) return null

        return (n * sumXY - sumX * sumY) / denominator
    }

    internal fun computeCoRiseRate(xs: List<Double>, ys: List<Double>): Double {
        if (xs.isEmpty()) return 0.0
        val coRiseCount = xs.indices.count { xs[it] > 0 && ys[it] > 0 }
        return coRiseCount.toDouble() / xs.size
    }

    private suspend fun batchUpsert(correlations: List<StockCorrelation>) {
        for (chunk in correlations.chunked(BATCH_SIZE)) {
            upsertChunk(chunk)
        }
    }

    private suspend fun upsertChunk(chunk: List<StockCorrelation>) {
        if (chunk.isEmpty()) return

        val valuePlaceholders = chunk.indices.joinToString(", ") { i ->
            "(:codeA$i, :codeB$i, :corr$i, :coRise$i, :window$i, NOW())"
        }

        val sql = """
            INSERT INTO stock_correlation (stock_code_a, stock_code_b, correlation, co_rise_rate, window_days, calculated_at)
            VALUES $valuePlaceholders
            ON CONFLICT (stock_code_a, stock_code_b) DO UPDATE SET
                correlation = EXCLUDED.correlation,
                co_rise_rate = EXCLUDED.co_rise_rate,
                window_days = EXCLUDED.window_days,
                calculated_at = NOW()
        """.trimIndent()

        var spec = databaseClient.sql(sql)
        for ((i, c) in chunk.withIndex()) {
            spec = spec.bind("codeA$i", c.stockCodeA)
                .bind("codeB$i", c.stockCodeB)
                .bind("corr$i", c.correlation)
                .bind("coRise$i", c.coRiseRate)
                .bind("window$i", c.windowDays)
        }

        try {
            val updated = spec.fetch().rowsUpdated().awaitFirstOrNull()?.toInt() ?: 0
            log.debug("Upserted {} correlation rows", updated)
        } catch (e: Exception) {
            log.warn("Correlation upsert failed for chunk of {} rows: {}", chunk.size, e.message)
        }
    }

    companion object {
        internal const val WINDOW_DAYS = 90
        internal const val MIN_OVERLAP = 20
        private const val BATCH_SIZE = 100
    }
}
