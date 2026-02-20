package io.kaeum.marketlens.infrastructure.config

import org.springframework.boot.actuate.health.AbstractHealthIndicator
import org.springframework.boot.actuate.health.Health
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class CollectorHealthIndicator(
    private val marketTimeScheduler: MarketTimeScheduler,
) : AbstractHealthIndicator() {

    override fun doHealthCheck(builder: Health.Builder) {
        val details = mutableMapOf<String, Any>()

        details["tradingDay"] = marketTimeScheduler.isTradingDay()
        details["marketOpen"] = marketTimeScheduler.isMarketOpen()

        val lastBatch = marketTimeScheduler.lastDailyBatchResult.get()
        if (lastBatch != null) {
            details["lastDailyBatch"] = mapOf(
                "executedAt" to lastBatch.executedAt.toString(),
                "durationMs" to lastBatch.durationMs,
                "historicalSuccess" to lastBatch.historicalSuccess,
                "correlationSuccess" to lastBatch.correlationSuccess,
            )
        }

        builder.up().withDetails(details)
    }
}
