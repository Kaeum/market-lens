package io.kaeum.marketlens.domain.price

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface StockPriceSnapshotRepository : CoroutineCrudRepository<StockPriceSnapshot, String> {

    @Query("SELECT * FROM stock_price_snapshot WHERE stock_code IN (:stockCodes)")
    suspend fun findByStockCodeIn(stockCodes: List<String>): List<StockPriceSnapshot>
}
