package io.kaeum.marketlens.infrastructure.stream

data class VolumeAccumulator(
    val stockCode: String = "",
    val totalVolume: Long = 0,
    val tickCount: Int = 0,
    val maxPrice: Long = 0,
    val minPrice: Long = 0,
    val latestChangeRate: String = "0",
)
