package io.kaeum.marketlens.application.dto

data class ThemeLeaderResponse(
    val themeId: Long,
    val themeName: String,
    val themeNameKr: String,
    val leaders: List<StockSnapshotDto>,
)
