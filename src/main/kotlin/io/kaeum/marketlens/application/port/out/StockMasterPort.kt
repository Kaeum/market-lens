package io.kaeum.marketlens.application.port.out

data class StockMasterData(
    val stockCode: String,
    val stockName: String,
    val market: String,
)

interface StockMasterPort {
    suspend fun fetchAllStocks(): List<StockMasterData>
    suspend fun fetchStocksByMarket(market: String): List<StockMasterData>
}
