package io.kaeum.marketlens.application.port.`in`

import io.kaeum.marketlens.application.dto.StockResponse

interface StockQueryUseCase {
    suspend fun searchStocks(market: String?, keyword: String?): List<StockResponse>
    suspend fun getStockDetail(stockCode: String): StockResponse
}
