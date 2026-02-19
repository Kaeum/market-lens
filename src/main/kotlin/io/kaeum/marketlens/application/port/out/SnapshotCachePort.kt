package io.kaeum.marketlens.application.port.out

import io.kaeum.marketlens.domain.price.RealtimeTick
import io.kaeum.marketlens.domain.price.StockPriceSnapshot

interface SnapshotCachePort {

    suspend fun updateIfNewer(tick: RealtimeTick): Boolean

    suspend fun getSnapshot(stockCode: String): StockPriceSnapshot?

    suspend fun getSnapshots(stockCodes: List<String>): List<StockPriceSnapshot>

    suspend fun drainDirtyCodes(): Set<String>

    suspend fun warmUp(snapshots: List<StockPriceSnapshot>)
}
