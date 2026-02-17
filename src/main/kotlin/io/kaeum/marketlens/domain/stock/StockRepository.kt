package io.kaeum.marketlens.domain.stock

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface StockRepository : CoroutineCrudRepository<Stock, String> {

    suspend fun findByMarketAndIsActiveTrue(market: String): List<Stock>

    suspend fun findByIsActiveTrue(): List<Stock>

    @Query("SELECT * FROM stock WHERE is_active = true AND (stock_code ILIKE :keyword OR stock_name ILIKE :keyword)")
    suspend fun searchByKeyword(keyword: String): List<Stock>
}
