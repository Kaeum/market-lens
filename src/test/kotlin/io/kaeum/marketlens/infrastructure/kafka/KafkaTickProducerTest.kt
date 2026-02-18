package io.kaeum.marketlens.infrastructure.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kaeum.marketlens.domain.price.RealtimeTick
import io.kaeum.marketlens.domain.price.TickType
import io.kaeum.marketlens.infrastructure.config.KafkaTopics
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kafka.sender.KafkaSender
import reactor.kafka.sender.SenderRecord
import reactor.kafka.sender.SenderResult
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime

class KafkaTickProducerTest {

    private val kafkaSender: KafkaSender<String, String> = mockk()
    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val producer = KafkaTickProducer(kafkaSender, objectMapper)

    @Test
    fun `emit sends tick to tick_raw topic with stockCode as key`() = runTest {
        val tick = createTick("005930", 72000L)
        val recordSlot = slot<Mono<SenderRecord<String, String, String>>>()

        val senderResult = mockk<SenderResult<String>>()
        every { senderResult.exception() } returns null
        every { kafkaSender.send(capture(recordSlot)) } returns Flux.just(senderResult)

        producer.emit(tick)

        verify(exactly = 1) { kafkaSender.send(any<Mono<SenderRecord<String, String, String>>>()) }

        val capturedRecord = recordSlot.captured.block()!!
        assertEquals(KafkaTopics.TICK_RAW, capturedRecord.topic())
        assertEquals("005930", capturedRecord.key())
        assertEquals("005930", capturedRecord.correlationMetadata())
    }

    @Test
    fun `emit serializes tick as valid JSON`() = runTest {
        val tick = createTick("000660", 180000L)
        val recordSlot = slot<Mono<SenderRecord<String, String, String>>>()

        val senderResult = mockk<SenderResult<String>>()
        every { senderResult.exception() } returns null
        every { kafkaSender.send(capture(recordSlot)) } returns Flux.just(senderResult)

        producer.emit(tick)

        val capturedRecord = recordSlot.captured.block()!!
        val json = capturedRecord.value()
        val deserialized = objectMapper.readValue(json, RealtimeTick::class.java)

        assertEquals("000660", deserialized.stockCode)
        assertEquals(180000L, deserialized.currentPrice)
        assertEquals(TickType.TRADE, deserialized.tickType)
    }

    @Test
    fun `emit handles send failure gracefully`() = runTest {
        val tick = createTick("005930", 72000L)

        every { kafkaSender.send(any<Mono<SenderRecord<String, String, String>>>()) } returns
            Flux.error(RuntimeException("Kafka unavailable"))

        // Should not throw — logs warning instead
        producer.emit(tick)
    }

    private fun createTick(stockCode: String, price: Long) = RealtimeTick(
        stockCode = stockCode,
        currentPrice = price,
        changeRate = BigDecimal("1.50"),
        volume = 500,
        accumulatedVolume = 10_000_000,
        tradingValue = 50_000_000_000,
        tradeTime = LocalTime.of(10, 5, 30),
        eventTime = Instant.parse("2026-02-18T01:05:30Z"),
        tickType = TickType.TRADE,
    )
}
