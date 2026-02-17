package io.kaeum.marketlens.application.port.out

import io.kaeum.marketlens.domain.price.StockPriceSnapshot
import kotlinx.coroutines.flow.Flow

interface StockPricePort {
    suspend fun getLatestPrices(stockCodes: List<String>): List<StockPriceSnapshot>
    fun priceStream(stockCode: String): Flow<StockPriceSnapshot>
}
