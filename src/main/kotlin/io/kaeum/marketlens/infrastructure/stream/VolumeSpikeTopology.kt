package io.kaeum.marketlens.infrastructure.stream

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kaeum.marketlens.domain.price.RealtimeTick
import io.kaeum.marketlens.infrastructure.config.KafkaTopics
import kotlinx.coroutines.reactive.awaitSingle
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Suppressed
import org.apache.kafka.streams.kstream.TimeWindows
import org.apache.kafka.streams.state.WindowStore
import org.slf4j.LoggerFactory
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class VolumeSpikeTopology(
    private val objectMapper: ObjectMapper,
    private val databaseClient: DatabaseClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val avgVolumeCache = ConcurrentHashMap<String, Long>()

    companion object {
        const val WINDOW_SIZE_MINUTES = 10L
        const val GRACE_PERIOD_SECONDS = 30L
        const val WINDOWS_PER_DAY = 39 // 09:00~15:30 = 390분 / 10분
        const val STATE_STORE_NAME = "volume-accumulator-store"

        private const val AVG_VOLUME_SQL = """
            SELECT stock_code, AVG(total_volume)::BIGINT AS avg_volume
            FROM stock_price_daily
            WHERE bucket >= CURRENT_DATE - INTERVAL '20 days'
            GROUP BY stock_code
        """
    }

    fun buildTopology(builder: StreamsBuilder) {
        val jsonSerde = Serdes.String()

        val window = TimeWindows
            .ofSizeAndGrace(
                Duration.ofMinutes(WINDOW_SIZE_MINUTES),
                Duration.ofSeconds(GRACE_PERIOD_SECONDS),
            )

        builder
            .stream<String, String>(KafkaTopics.TICK_RAW)
            .mapValues { value ->
                runCatching { objectMapper.readValue<RealtimeTick>(value) }.getOrNull()
            }
            .filter { _, tick -> tick != null }
            .mapValues { tick -> tick!! }
            .groupByKey()
            .windowedBy(window)
            .aggregate(
                { VolumeAccumulator() },
                { key, tick, acc ->
                    acc.copy(
                        stockCode = key,
                        totalVolume = acc.totalVolume + tick.volume,
                        tickCount = acc.tickCount + 1,
                        maxPrice = maxOf(acc.maxPrice, tick.currentPrice),
                        minPrice = if (acc.minPrice == 0L) tick.currentPrice else minOf(acc.minPrice, tick.currentPrice),
                        latestChangeRate = tick.changeRate.toPlainString(),
                    )
                },
                Materialized.`as`<String, VolumeAccumulator, WindowStore<Bytes, ByteArray>>(STATE_STORE_NAME)
                    .withKeySerde(Serdes.String())
                    .withValueSerde(jsonSerde(VolumeAccumulator::class.java)),
            )
            .suppress(Suppressed.untilWindowCloses(Suppressed.BufferConfig.unbounded()))
            .toStream()
            .map { windowedKey, acc ->
                val stockCode = windowedKey.key()
                val avgDailyVolume = avgVolumeCache[stockCode] ?: 0L
                val avgWindowVolume = if (avgDailyVolume > 0) avgDailyVolume / WINDOWS_PER_DAY else 0L
                val spikeRatio = if (avgWindowVolume > 0) acc.totalVolume.toDouble() / avgWindowVolume else 0.0

                val event = VolumeSpikeEvent(
                    stockCode = stockCode,
                    windowVolume = acc.totalVolume,
                    avgWindowVolume = avgWindowVolume,
                    spikeRatio = spikeRatio,
                    windowStart = Instant.ofEpochMilli(windowedKey.window().start()),
                    windowEnd = Instant.ofEpochMilli(windowedKey.window().end()),
                )
                KeyValue(stockCode, objectMapper.writeValueAsString(event))
            }
            .to(KafkaTopics.VOLUME_SPIKE)
    }

    suspend fun refreshAvgVolumes() {
        try {
            val volumes = databaseClient.sql(AVG_VOLUME_SQL)
                .map { row, _ ->
                    val stockCode = row.get("stock_code", String::class.java)!!
                    val avgVolume = row.get("avg_volume", java.lang.Long::class.java)?.toLong() ?: 0L
                    stockCode to avgVolume
                }
                .all()
                .collectList()
                .awaitSingle()

            avgVolumeCache.clear()
            volumes.forEach { (code, vol) -> avgVolumeCache[code] = vol }
            log.info("Refreshed average volumes for {} stocks", volumes.size)
        } catch (e: Exception) {
            log.error("Failed to refresh average volumes: {}", e.message, e)
        }
    }

    fun getAvgVolumeCache(): Map<String, Long> = avgVolumeCache.toMap()

    private fun <T> jsonSerde(clazz: Class<T>): org.apache.kafka.common.serialization.Serde<T> {
        val serializer = org.apache.kafka.common.serialization.Serializer<T> { _, data ->
            objectMapper.writeValueAsBytes(data)
        }
        val deserializer = org.apache.kafka.common.serialization.Deserializer<T> { _, bytes ->
            if (bytes == null) null else objectMapper.readValue(bytes, clazz)
        }
        return Serdes.serdeFrom(serializer, deserializer)
    }
}
