package io.kaeum.marketlens.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import io.kaeum.marketlens.application.port.out.TickProducer
import io.kaeum.marketlens.domain.price.RealtimeTick
import io.kaeum.marketlens.infrastructure.config.KafkaTopics
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.kafka.sender.KafkaSender
import reactor.kafka.sender.SenderRecord

@Component
@Primary
@Profile("!test")
class KafkaTickProducer(
    private val kafkaSender: KafkaSender<String, String>,
    private val objectMapper: ObjectMapper,
) : TickProducer {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun emit(tick: RealtimeTick) {
        try {
            val json = objectMapper.writeValueAsString(tick)
            val record = SenderRecord.create(
                ProducerRecord(KafkaTopics.TICK_RAW, tick.stockCode, json),
                tick.stockCode,
            )
            kafkaSender.send(Mono.just(record)).awaitFirstOrNull()
        } catch (e: Exception) {
            log.warn("Failed to send tick to Kafka for {}: {}", tick.stockCode, e.message)
        }
    }
}
