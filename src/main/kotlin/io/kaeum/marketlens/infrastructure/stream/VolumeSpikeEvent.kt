package io.kaeum.marketlens.infrastructure.stream

import java.time.Instant

data class VolumeSpikeEvent(
    val stockCode: String,
    val windowVolume: Long,
    val avgWindowVolume: Long,
    val spikeRatio: Double,
    val windowStart: Instant,
    val windowEnd: Instant,
)
