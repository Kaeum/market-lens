package io.kaeum.marketlens.infrastructure.redis

import io.kaeum.marketlens.application.port.out.SnapshotCachePort
import io.kaeum.marketlens.domain.price.StockPriceSnapshotRepository
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class SnapshotRedisWarmUp(
    private val snapshotCachePort: SnapshotCachePort,
    private val snapshotRepository: StockPriceSnapshotRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun warmUp() {
        runBlocking {
            try {
                val snapshots = snapshotRepository.findAll().toList()
                snapshotCachePort.warmUp(snapshots)
                log.info("Redis warm-up completed: {} snapshots loaded from DB", snapshots.size)
            } catch (e: Exception) {
                log.warn("Redis warm-up failed: {}", e.message, e)
            }
        }
    }
}
