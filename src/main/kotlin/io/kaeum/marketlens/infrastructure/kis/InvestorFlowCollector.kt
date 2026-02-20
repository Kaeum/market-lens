package io.kaeum.marketlens.infrastructure.kis

import io.kaeum.marketlens.domain.investorflow.InvestorFlow
import io.kaeum.marketlens.infrastructure.config.MarketTimeScheduler
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
class InvestorFlowCollector(
    private val kisApiClient: KisApiClient,
    private val databaseClient: DatabaseClient,
    private val marketTimeScheduler: MarketTimeScheduler,
    private val meterRegistry: MeterRegistry,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pollingJob: Job? = null

    private val timer = Timer.builder("collector.investor_flow.duration")
        .description("Investor flow collection cycle duration")
        .register(meterRegistry)
    private val successCounter = meterRegistry.counter("collector.investor_flow.success")
    private val failureCounter = meterRegistry.counter("collector.investor_flow.failure")

    @PostConstruct
    fun init() {
        pollingJob = scope.launch {
            while (isActive) {
                if (!marketTimeScheduler.isTradingDay()) {
                    log.debug("Non-trading day, skipping investor flow polling")
                    delay(NON_TRADING_DAY_CHECK_INTERVAL_MS)
                    continue
                }
                try {
                    val sample = Timer.start(meterRegistry)
                    runPollingCycle()
                    sample.stop(timer)
                    successCounter.increment()
                } catch (e: Exception) {
                    failureCounter.increment()
                    log.error("Investor flow polling cycle failed: {}", e.message, e)
                }
                delay(POLLING_INTERVAL_MS)
            }
        }
        log.info("InvestorFlowCollector started with {}ms polling interval", POLLING_INTERVAL_MS)
    }

    @PreDestroy
    fun destroy() {
        pollingJob?.cancel()
        log.info("InvestorFlowCollector stopped")
    }

    suspend fun runPollingCycle() {
        val stockCodes = fetchActiveThemeStockCodes()
        if (stockCodes.isEmpty()) {
            log.debug("No active theme stocks found, skipping investor flow collection")
            return
        }

        log.info("Starting investor flow collection for {} stocks", stockCodes.size)

        val today = LocalDate.now(KST)
        val startDate = getPreviousTradingDay(today, LOOKBACK_TRADING_DAYS)
        val endDate = today

        val allFlows = mutableListOf<InvestorFlow>()
        var successCount = 0
        var failCount = 0

        for ((index, stockCode) in stockCodes.withIndex()) {
            if (index > 0) {
                delay(RATE_LIMIT_DELAY_MS)
            }
            try {
                val items = kisApiClient.fetchInvestorFlow(stockCode, startDate, endDate)
                items.forEach { item ->
                    allFlows.add(
                        InvestorFlow(
                            stockCode = stockCode,
                            tradingDate = endDate,
                            investorType = item.investorName,
                            sellVolume = item.sellVolume,
                            buyVolume = item.buyVolume,
                            netVolume = item.netVolume,
                            sellAmount = item.sellAmount,
                            buyAmount = item.buyAmount,
                            netAmount = item.netAmount,
                        )
                    )
                }
                successCount++
            } catch (e: Exception) {
                failCount++
                log.warn("Failed to fetch investor flow for {}: {}", stockCode, e.message)
            }
        }

        if (allFlows.isNotEmpty()) {
            val upserted = batchUpsert(allFlows)
            log.info(
                "Investor flow collection completed: {} stocks succeeded, {} failed, {} rows upserted",
                successCount, failCount, upserted,
            )
        } else {
            log.info("No investor flow data collected (success={}, fail={})", successCount, failCount)
        }
    }

    internal suspend fun fetchActiveThemeStockCodes(): List<String> {
        return try {
            databaseClient.sql(
                """
                SELECT DISTINCT ts.stock_code
                FROM theme_stock ts
                JOIN stock s ON ts.stock_code = s.stock_code
                WHERE s.is_active = TRUE
                """.trimIndent()
            )
                .map { row, _ -> row.get("stock_code", String::class.java)!! }
                .all()
                .collectList()
                .awaitFirstOrNull() ?: emptyList()
        } catch (e: Exception) {
            log.error("Failed to fetch active theme stock codes: {}", e.message, e)
            emptyList()
        }
    }

    internal suspend fun batchUpsert(flows: List<InvestorFlow>): Int {
        if (flows.isEmpty()) return 0

        var totalUpserted = 0
        for (chunk in flows.chunked(BATCH_SIZE)) {
            totalUpserted += upsertChunk(chunk)
        }
        return totalUpserted
    }

    private suspend fun upsertChunk(chunk: List<InvestorFlow>): Int {
        if (chunk.isEmpty()) return 0

        val valuePlaceholders = chunk.indices.joinToString(", ") { i ->
            "(:time$i, :code$i, :type$i, :sv$i, :bv$i, :nv$i, :sa$i, :ba$i, :na$i)"
        }

        val sql = """
            INSERT INTO investor_flow (time, stock_code, investor_type, sell_volume, buy_volume, net_volume, sell_amount, buy_amount, net_amount)
            VALUES $valuePlaceholders
            ON CONFLICT (stock_code, time, investor_type) DO UPDATE SET
                sell_volume = EXCLUDED.sell_volume, buy_volume = EXCLUDED.buy_volume,
                net_volume = EXCLUDED.net_volume, sell_amount = EXCLUDED.sell_amount,
                buy_amount = EXCLUDED.buy_amount, net_amount = EXCLUDED.net_amount,
                collected_at = NOW()
        """.trimIndent()

        var spec = databaseClient.sql(sql)
        for ((i, flow) in chunk.withIndex()) {
            val time = flow.tradingDate.atStartOfDay().atZone(KST).toInstant()
            spec = spec.bind("time$i", time)
                .bind("code$i", flow.stockCode)
                .bind("type$i", flow.investorType)
                .bind("sv$i", flow.sellVolume)
                .bind("bv$i", flow.buyVolume)
                .bind("nv$i", flow.netVolume)
                .bind("sa$i", flow.sellAmount)
                .bind("ba$i", flow.buyAmount)
                .bind("na$i", flow.netAmount)
        }

        return try {
            spec.fetch().rowsUpdated().awaitFirstOrNull()?.toInt() ?: 0
        } catch (e: Exception) {
            log.warn("Batch upsert failed for chunk of {} rows: {}", chunk.size, e.message)
            0
        }
    }

    internal fun getPreviousTradingDay(from: LocalDate, tradingDaysBack: Int): LocalDate {
        var date = from
        var count = 0
        while (count < tradingDaysBack) {
            date = date.minusDays(1)
            if (!isWeekend(date)) {
                count++
            }
        }
        return date
    }

    private fun isWeekend(date: LocalDate): Boolean {
        return date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    }

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
        private const val POLLING_INTERVAL_MS = 20 * 60 * 1000L // 20 minutes
        private const val NON_TRADING_DAY_CHECK_INTERVAL_MS = 60 * 60 * 1000L // 1 hour
        private const val RATE_LIMIT_DELAY_MS = 200L
        private const val BATCH_SIZE = 100
        private const val LOOKBACK_TRADING_DAYS = 5
    }
}
