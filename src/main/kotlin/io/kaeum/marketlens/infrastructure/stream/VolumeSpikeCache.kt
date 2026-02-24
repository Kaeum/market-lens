package io.kaeum.marketlens.infrastructure.stream

import io.kaeum.marketlens.application.port.out.VolumeSpikePort
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class VolumeSpikeCache : VolumeSpikePort {

    private val spikeMap = ConcurrentHashMap<String, VolumeSpikeEvent>()

    fun update(event: VolumeSpikeEvent) {
        spikeMap[event.stockCode] = event
    }

    fun getEvent(stockCode: String): VolumeSpikeEvent? = spikeMap[stockCode]

    override suspend fun getSpikeRatio(stockCode: String): Double? =
        spikeMap[stockCode]?.spikeRatio

    override suspend fun getSpikeRatios(stockCodes: List<String>): Map<String, Double> =
        stockCodes.mapNotNull { code -> spikeMap[code]?.let { code to it.spikeRatio } }.toMap()
}
