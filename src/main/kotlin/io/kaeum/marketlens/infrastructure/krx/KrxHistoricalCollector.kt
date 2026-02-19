package io.kaeum.marketlens.infrastructure.krx

import io.kaeum.marketlens.domain.price.StockDailyPrice
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

@Component
@Profile("!test")
class KrxHistoricalCollector(
    private val krxApiClient: KrxApiClient,
    private val databaseClient: DatabaseClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @PostConstruct
    fun init() {
        scope.launch {
            try {
                val count = countStockPriceRows()
                if (count == 0L) {
                    log.info("stock_price table is empty, starting 60-day backfill")
                    backfill()
                } else {
                    log.info("stock_price table has {} rows, skipping backfill", count)
                }
            } catch (e: Exception) {
                log.error("Backfill check/execution failed: {}", e.message, e)
            }
        }
    }

    private suspend fun countStockPriceRows(): Long {
        return databaseClient.sql("SELECT count(*) AS cnt FROM stock_price")
            .map { row, _ -> row.get("cnt", java.lang.Long::class.java)?.toLong() ?: 0L }
            .first()
            .awaitFirstOrNull() ?: 0L
    }

    suspend fun backfill(days: Int = 60) {
        val businessDays = getBusinessDays(days)
        log.info("Starting backfill for {} business days (from {} to {})",
            businessDays.size, businessDays.lastOrNull(), businessDays.firstOrNull())

        var totalInserted = 0
        for ((index, date) in businessDays.reversed().withIndex()) {
            try {
                val inserted = collectDate(date)
                totalInserted += inserted
                log.info("Backfill [{}/{}] date={} inserted={} total={}",
                    index + 1, businessDays.size, date, inserted, totalInserted)
            } catch (e: Exception) {
                log.warn("Backfill failed for date={}: {}", date, e.message)
            }
            delay(INTER_REQUEST_DELAY_MS)
        }

        log.info("Backfill completed: {} rows inserted across {} days", totalInserted, businessDays.size)
    }

    suspend fun collectDate(date: LocalDate): Int {
        val kospiPrices = try {
            krxApiClient.fetchDailyOhlcv(date, MARKET_KOSPI)
        } catch (e: Exception) {
            log.warn("KOSPI fetch failed for {}: {}", date, e.message)
            emptyList()
        }

        delay(INTER_REQUEST_DELAY_MS)

        val kosdaqPrices = try {
            krxApiClient.fetchDailyOhlcv(date, MARKET_KOSDAQ)
        } catch (e: Exception) {
            log.warn("KOSDAQ fetch failed for {}: {}", date, e.message)
            emptyList()
        }

        val allPrices = kospiPrices + kosdaqPrices
        if (allPrices.isEmpty()) {
            log.debug("No data for date={} (likely holiday)", date)
            return 0
        }

        return batchInsert(allPrices)
    }

    suspend fun runDailyBatch() {
        val yesterday = LocalDate.now(KST).minusDays(1)
        if (isWeekend(yesterday)) {
            log.info("Skipping daily batch for weekend date={}", yesterday)
            return
        }
        log.info("Running daily batch for date={}", yesterday)
        val inserted = collectDate(yesterday)
        log.info("Daily batch completed: {} rows inserted for date={}", inserted, yesterday)
    }

    private suspend fun batchInsert(prices: List<StockDailyPrice>): Int {
        if (prices.isEmpty()) return 0

        val batchSize = BATCH_SIZE
        var totalInserted = 0

        for (chunk in prices.chunked(batchSize)) {
            val inserted = insertChunk(chunk)
            totalInserted += inserted
        }

        return totalInserted
    }

    private suspend fun insertChunk(chunk: List<StockDailyPrice>): Int {
        if (chunk.isEmpty()) return 0

        val valuePlaceholders = chunk.indices.joinToString(", ") { i ->
            "(:code$i, :time$i, :close$i, :change$i, :vol$i, :cap$i, :open$i, :high$i, :low$i)"
        }

        val sql = """
            INSERT INTO stock_price (stock_code, time, current_price, change_rate, volume, market_cap, open_price, high_price, low_price)
            VALUES $valuePlaceholders
            ON CONFLICT (stock_code, time) DO NOTHING
        """.trimIndent()

        var spec = databaseClient.sql(sql)
        for ((i, price) in chunk.withIndex()) {
            val time = price.date.atStartOfDay().atZone(KST).toInstant()
            spec = spec.bind("code$i", price.stockCode)
                .bind("time$i", time)
                .bind("close$i", price.closePrice)
                .bind("change$i", price.changeRate ?: java.math.BigDecimal.ZERO)
                .bind("vol$i", price.volume)
                .bind("open$i", price.openPrice)
                .bind("high$i", price.highPrice)
                .bind("low$i", price.lowPrice)
            if (price.marketCap != null) {
                spec = spec.bind("cap$i", price.marketCap)
            } else {
                spec = spec.bindNull("cap$i", java.lang.Long::class.java)
            }
        }

        return try {
            spec.fetch().rowsUpdated().awaitFirstOrNull()?.toInt() ?: 0
        } catch (e: Exception) {
            log.warn("Batch insert failed for chunk of {} rows: {}", chunk.size, e.message)
            0
        }
    }

    private fun getBusinessDays(count: Int): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var date = LocalDate.now(KST).minusDays(1)
        while (result.size < count) {
            if (!isWeekend(date)) {
                result.add(date)
            }
            date = date.minusDays(1)
        }
        return result
    }

    private fun isWeekend(date: LocalDate): Boolean {
        return date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    }

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
        private const val MARKET_KOSPI = "KOSPI"
        private const val MARKET_KOSDAQ = "KOSDAQ"
        private const val INTER_REQUEST_DELAY_MS = 500L
        private const val BATCH_SIZE = 500
    }
}
