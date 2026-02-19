package io.kaeum.marketlens.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kaeum.marketlens.application.port.out.SnapshotCachePort
import io.kaeum.marketlens.domain.price.RealtimeTick
import io.kaeum.marketlens.infrastructure.config.KafkaTopics
import io.kaeum.marketlens.infrastructure.kis.InMemoryTickProducer
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.Disposable
import reactor.kafka.receiver.KafkaReceiver
import reactor.kafka.receiver.ReceiverOptions

@Component
@Profile("!test")
class KafkaTickConsumer(
    private val receiverOptions: ReceiverOptions<String, String>,
    private val inMemoryTickProducer: InMemoryTickProducer,
    private val snapshotCachePort: SnapshotCachePort,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var disposable: Disposable? = null

    @PostConstruct
    fun start() {
        val options = receiverOptions.subscription(listOf(KafkaTopics.TICK_RAW))
        disposable = KafkaReceiver.create(options)
            .receive()
            .doOnNext { record ->
                try {
                    val tick = objectMapper.readValue<RealtimeTick>(record.value())
                    scope.launch {
                        try {
                            snapshotCachePort.updateIfNewer(tick)
                        } catch (e: Exception) {
                            log.warn("Failed to update Redis snapshot for {}: {}", tick.stockCode, e.message)
                        }
                        inMemoryTickProducer.emit(tick)
                    }
                } catch (e: Exception) {
                    log.warn("Failed to deserialize tick record: {}", e.message)
                }
                record.receiverOffset().acknowledge()
            }
            .doOnError { e ->
                log.error("Kafka consumer error: {}", e.message, e)
            }
            .subscribe()
        log.info("Started Kafka consumer for topic: {}", KafkaTopics.TICK_RAW)
    }

    @PreDestroy
    fun stop() {
        log.info("Stopping Kafka consumer for topic: {}", KafkaTopics.TICK_RAW)
        disposable?.dispose()
        scope.cancel()
    }
}
