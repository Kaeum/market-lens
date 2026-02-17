package io.kaeum.marketlens.application.dto

import java.math.BigDecimal

data class StockResponse(
    val stockCode: String,
    val stockName: String,
    val market: String,
    val currentPrice: Long?,
    val changeRate: BigDecimal?,
    val volume: Long?,
    val marketCap: Long?,
)
