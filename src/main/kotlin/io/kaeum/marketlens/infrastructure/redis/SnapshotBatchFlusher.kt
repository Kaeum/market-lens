package io.kaeum.marketlens.infrastructure.redis

import io.kaeum.marketlens.application.port.out.SnapshotCachePort
import io.kaeum.marketlens.domain.price.StockPriceSnapshot
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
@Profile("!test")
class SnapshotBatchFlusher(
    private val snapshotCachePort: SnapshotCachePort,
    private val databaseClient: DatabaseClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    @PostConstruct
    fun start() {
        job = scope.launch {
            log.info("Started snapshot batch flusher (1s interval)")
            while (isActive) {
                try {
                    flush()
                } catch (e: Exception) {
                    log.error("Snapshot flush failed: {}", e.message, e)
                }
                delay(FLUSH_INTERVAL_MS)
            }
        }
    }

    @PreDestroy
    fun stop() {
        log.info("Stopping snapshot batch flusher")
        runBlocking {
            try {
                flush()
            } catch (e: Exception) {
                log.warn("Final flush failed: {}", e.message, e)
            }
        }
        scope.cancel()
    }

    internal suspend fun flush() {
        val dirtyCodes = snapshotCachePort.drainDirtyCodes()
        if (dirtyCodes.isEmpty()) return

        val snapshots = snapshotCachePort.getSnapshots(dirtyCodes.toList())
        if (snapshots.isEmpty()) return

        var upserted = 0
        for (snapshot in snapshots) {
            try {
                upsertSnapshot(snapshot)
                upserted++
            } catch (e: Exception) {
                log.warn("Failed to upsert snapshot for {}: {}", snapshot.stockCode, e.message)
            }
        }

        if (upserted > 0) {
            log.debug("Flushed {} snapshots to DB", upserted)
        }
    }

    private suspend fun upsertSnapshot(snapshot: StockPriceSnapshot) {
        databaseClient.sql(UPSERT_SQL)
            .bind("stockCode", snapshot.stockCode)
            .bind("currentPrice", snapshot.currentPrice)
            .bind("changeRate", snapshot.changeRate ?: java.math.BigDecimal.ZERO)
            .bind("volume", snapshot.volume)
            .bind("tradingValue", snapshot.tradingValue)
            .bind("updatedAt", snapshot.updatedAt)
            .then()
            .awaitFirstOrNull()
    }

    companion object {
        private const val FLUSH_INTERVAL_MS = 1000L

        private val UPSERT_SQL = """
            INSERT INTO stock_price_snapshot (stock_code, current_price, change_rate, volume, trading_value, updated_at)
            VALUES (:stockCode, :currentPrice, :changeRate, :volume, :tradingValue, :updatedAt)
            ON CONFLICT (stock_code) DO UPDATE SET
                current_price = EXCLUDED.current_price,
                change_rate = EXCLUDED.change_rate,
                volume = EXCLUDED.volume,
                trading_value = EXCLUDED.trading_value,
                updated_at = EXCLUDED.updated_at
            WHERE stock_price_snapshot.updated_at < EXCLUDED.updated_at
        """.trimIndent()
    }
}
