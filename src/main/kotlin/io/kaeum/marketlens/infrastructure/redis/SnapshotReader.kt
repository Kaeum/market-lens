package io.kaeum.marketlens.infrastructure.redis

import io.kaeum.marketlens.application.port.out.SnapshotCachePort
import io.kaeum.marketlens.domain.price.StockPriceSnapshot
import io.kaeum.marketlens.domain.price.StockPriceSnapshotRepository
import org.springframework.stereotype.Component

@Component
class SnapshotReader(
    private val snapshotCachePort: SnapshotCachePort?,
    private val snapshotRepository: StockPriceSnapshotRepository,
) {

    suspend fun findByStockCodeIn(stockCodes: List<String>): List<StockPriceSnapshot> {
        if (stockCodes.isEmpty()) return emptyList()
        if (snapshotCachePort == null) {
            return snapshotRepository.findByStockCodeIn(stockCodes)
        }

        val cached = snapshotCachePort.getSnapshots(stockCodes)
        val cachedCodes = cached.map { it.stockCode }.toSet()
        val missingCodes = stockCodes.filter { it !in cachedCodes }

        val fromDb = if (missingCodes.isNotEmpty()) {
            snapshotRepository.findByStockCodeIn(missingCodes)
        } else {
            emptyList()
        }

        return cached + fromDb
    }

    suspend fun findById(stockCode: String): StockPriceSnapshot? {
        snapshotCachePort?.getSnapshot(stockCode)?.let { return it }
        return snapshotRepository.findById(stockCode)
    }
}
