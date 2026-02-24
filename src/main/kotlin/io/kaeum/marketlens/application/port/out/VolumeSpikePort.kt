package io.kaeum.marketlens.application.port.out

interface VolumeSpikePort {
    suspend fun getSpikeRatio(stockCode: String): Double?
    suspend fun getSpikeRatios(stockCodes: List<String>): Map<String, Double>
}
