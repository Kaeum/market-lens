package io.kaeum.marketlens.application.port.out

import io.kaeum.marketlens.domain.price.StockPriceSnapshot

interface SnapshotReadPort {
    suspend fun findByStockCodeIn(stockCodes: List<String>): List<StockPriceSnapshot>
    suspend fun findById(stockCode: String): StockPriceSnapshot?
}
