package io.kaeum.marketlens.infrastructure.stream

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kaeum.marketlens.infrastructure.config.KafkaTopics
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.Disposable
import reactor.kafka.receiver.KafkaReceiver
import reactor.kafka.receiver.ReceiverOptions

@Component
@Profile("!test")
class VolumeSpikeConsumer(
    private val receiverOptions: ReceiverOptions<String, String>,
    private val volumeSpikeCache: VolumeSpikeCache,
    private val objectMapper: ObjectMapper,
    @Value("\${kafka.bootstrap-servers}") private val bootstrapServers: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private var disposable: Disposable? = null

    companion object {
        private const val CONSUMER_GROUP_ID = "market-lens-volume-spike-cache"
    }

    @PostConstruct
    fun start() {
        val props = receiverOptions.consumerProperties().toMutableMap()
        props[ConsumerConfig.GROUP_ID_CONFIG] = CONSUMER_GROUP_ID

        val options = ReceiverOptions.create<String, String>(props)
            .subscription(listOf(KafkaTopics.VOLUME_SPIKE))

        disposable = KafkaReceiver.create(options)
            .receive()
            .doOnNext { record ->
                try {
                    val event = objectMapper.readValue<VolumeSpikeEvent>(record.value())
                    volumeSpikeCache.update(event)
                } catch (e: Exception) {
                    log.warn("Failed to deserialize volume spike event: {}", e.message)
                }
                record.receiverOffset().acknowledge()
            }
            .doOnError { e ->
                log.error("Volume spike consumer error: {}", e.message, e)
            }
            .subscribe()

        log.info("Started volume spike consumer for topic: {}", KafkaTopics.VOLUME_SPIKE)
    }

    @PreDestroy
    fun stop() {
        log.info("Stopping volume spike consumer")
        disposable?.dispose()
    }
}
