package io.kaeum.marketlens.domain.price

import java.math.BigDecimal
import java.time.LocalDate

data class StockDailyPrice(
    val stockCode: String,
    val date: LocalDate,
    val openPrice: Long,
    val highPrice: Long,
    val lowPrice: Long,
    val closePrice: Long,
    val volume: Long,
    val changeRate: BigDecimal?,
    val marketCap: Long?,
)
