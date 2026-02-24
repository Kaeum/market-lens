package io.kaeum.marketlens.infrastructure.stream

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.runBlocking
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.time.Duration
import java.util.Properties

@Configuration
@Profile("!test")
class KafkaStreamsConfig(
    @Value("\${kafka.bootstrap-servers}") private val bootstrapServers: String,
    private val volumeSpikeTopology: VolumeSpikeTopology,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private var streams: KafkaStreams? = null

    @Bean
    fun kafkaStreamsInstance(): KafkaStreams {
        val props = Properties().apply {
            put(StreamsConfig.APPLICATION_ID_CONFIG, "market-lens-volume-spike")
            put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, "org.apache.kafka.common.serialization.Serdes\$StringSerde")
            put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, "org.apache.kafka.common.serialization.Serdes\$StringSerde")
            put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2)
            put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000)
        }

        val builder = StreamsBuilder()
        volumeSpikeTopology.buildTopology(builder)

        val topology = builder.build()
        log.info("Kafka Streams topology:\n{}", topology.describe())

        return KafkaStreams(topology, props).also { ks ->
            ks.setUncaughtExceptionHandler { throwable ->
                log.error("Kafka Streams uncaught exception: {}", throwable.message, throwable)
                StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.REPLACE_THREAD
            }

            runBlocking { volumeSpikeTopology.refreshAvgVolumes() }

            ks.start()
            streams = ks
            log.info("Kafka Streams started with application.id: market-lens-volume-spike")
        }
    }

    @PreDestroy
    fun stop() {
        log.info("Stopping Kafka Streams...")
        streams?.close(Duration.ofSeconds(10))
    }
}
