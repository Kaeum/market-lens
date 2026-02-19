package io.kaeum.marketlens.domain.correlation

import java.math.BigDecimal

data class StockCorrelation(
    val stockCodeA: String,
    val stockCodeB: String,
    val correlation: BigDecimal,
    val coRiseRate: BigDecimal,
    val windowDays: Int,
)
