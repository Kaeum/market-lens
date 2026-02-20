package io.kaeum.marketlens.infrastructure.config

import io.kaeum.marketlens.infrastructure.krx.CorrelationCalculator
import io.kaeum.marketlens.infrastructure.krx.KrxHistoricalCollector
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference

@Component
@Profile("!test")
class MarketTimeScheduler(
    private val krxHistoricalCollector: KrxHistoricalCollector,
    private val correlationCalculator: CorrelationCalculator,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var dailyBatchJob: Job? = null

    val lastDailyBatchResult = AtomicReference<DailyBatchResult?>(null)

    @PostConstruct
    fun init() {
        dailyBatchJob = scope.launch {
            while (isActive) {
                try {
                    waitUntilDailyBatchTime()
                    if (!isActive) break
                    if (isTradingDay()) {
                        runDailyBatch()
                    } else {
                        log.info("Non-trading day, skipping daily batch")
                    }
                    // 배치 실행 후 다음날까지 대기 (최소 1시간)
                    delay(MIN_POST_BATCH_DELAY_MS)
                } catch (e: Exception) {
                    log.error("Daily batch scheduling loop failed: {}", e.message, e)
                    delay(RETRY_DELAY_MS)
                }
            }
        }
        log.info("MarketTimeScheduler started (daily batch at {})", DAILY_BATCH_TIME)
    }

    @PreDestroy
    fun destroy() {
        dailyBatchJob?.cancel()
        log.info("MarketTimeScheduler stopped")
    }

    fun isMarketOpen(): Boolean {
        if (!isTradingDay()) return false
        val now = LocalTime.now(KST)
        return !now.isBefore(MARKET_OPEN) && now.isBefore(MARKET_CLOSE)
    }

    fun isTradingDay(): Boolean {
        val today = LocalDate.now(KST)
        return isWeekday(today)
    }

    private fun isWeekday(date: LocalDate): Boolean {
        return date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
    }

    private suspend fun waitUntilDailyBatchTime() {
        val now = ZonedDateTime.now(KST)
        var target = now.toLocalDate().atTime(DAILY_BATCH_TIME).atZone(KST)

        // 이미 오늘 배치 시간이 지났으면 내일로
        if (now.isAfter(target)) {
            target = target.plusDays(1)
        }

        val waitMs = Duration.between(now, target).toMillis()
        if (waitMs > 0) {
            log.debug("Waiting {}ms until daily batch time ({})", waitMs, target)
            delay(waitMs)
        }
    }

    private suspend fun runDailyBatch() {
        log.info("Starting daily batch execution")
        val startTime = System.currentTimeMillis()
        var historicalSuccess = false
        var correlationSuccess = false

        try {
            krxHistoricalCollector.runDailyBatch()
            historicalSuccess = true
            log.info("Historical daily batch completed successfully")
        } catch (e: Exception) {
            log.error("Historical daily batch failed: {}", e.message, e)
        }

        try {
            correlationCalculator.runDailyBatch()
            correlationSuccess = true
            log.info("Correlation daily batch completed successfully")
        } catch (e: Exception) {
            log.error("Correlation daily batch failed: {}", e.message, e)
        }

        val duration = System.currentTimeMillis() - startTime
        val result = DailyBatchResult(
            executedAt = ZonedDateTime.now(KST).toInstant(),
            durationMs = duration,
            historicalSuccess = historicalSuccess,
            correlationSuccess = correlationSuccess,
        )
        lastDailyBatchResult.set(result)

        log.info(
            "Daily batch completed in {}ms (historical={}, correlation={})",
            duration, historicalSuccess, correlationSuccess,
        )
    }

    companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        val MARKET_OPEN: LocalTime = LocalTime.of(9, 0)
        val MARKET_CLOSE: LocalTime = LocalTime.of(15, 30)
        private val DAILY_BATCH_TIME = LocalTime.of(16, 0)
        private const val MIN_POST_BATCH_DELAY_MS = 60 * 60 * 1000L  // 1시간
        private const val RETRY_DELAY_MS = 5 * 60 * 1000L            // 5분
    }
}

data class DailyBatchResult(
    val executedAt: java.time.Instant,
    val durationMs: Long,
    val historicalSuccess: Boolean,
    val correlationSuccess: Boolean,
)
