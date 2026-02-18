package io.kaeum.marketlens.infrastructure.config

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import reactor.kafka.receiver.ReceiverOptions
import reactor.kafka.sender.KafkaSender
import reactor.kafka.sender.SenderOptions

@Configuration
@Profile("!test")
class KafkaConfig(
    @Value("\${kafka.bootstrap-servers}") private val bootstrapServers: String,
) {

    companion object {
        private const val CONSUMER_GROUP_ID = "market-lens-ticker"
        private const val ACKS_ALL = "all"
        private const val OFFSET_RESET_LATEST = "latest"
        private const val TICKER_RAW_PARTITIONS = 16
        private const val REPLICATION_FACTOR: Short = 1
    }

    @Bean
    fun kafkaSender(): KafkaSender<String, String> {
        val props = mapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to ACKS_ALL,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 5,
            ProducerConfig.LINGER_MS_CONFIG to 5,
        )
        return KafkaSender.create(SenderOptions.create(props))
    }

    @Bean
    fun kafkaReceiverOptions(): ReceiverOptions<String, String> {
        val props = mapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to CONSUMER_GROUP_ID,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to OFFSET_RESET_LATEST,
        )
        return ReceiverOptions.create(props)
    }

    @Bean
    fun tickerRawTopic(): NewTopic {
        return NewTopic(KafkaTopics.TICK_RAW, TICKER_RAW_PARTITIONS, REPLICATION_FACTOR)
    }
}
