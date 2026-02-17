package io.kaeum.marketlens.application.dto

import java.math.BigDecimal

data class ThemePerformanceResponse(
    val themeId: Long,
    val themeName: String,
    val themeNameKr: String,
    val avgChangeRate: BigDecimal,
    val totalVolume: Long,
    val stockCount: Int,
    val topStocks: List<StockSnapshotDto>,
)

data class StockSnapshotDto(
    val stockCode: String,
    val stockName: String,
    val currentPrice: Long,
    val changeRate: BigDecimal?,
    val volume: Long,
)
