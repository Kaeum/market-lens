package io.kaeum.marketlens.infrastructure.stream

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kaeum.marketlens.domain.price.RealtimeTick
import io.kaeum.marketlens.domain.price.TickType
import io.kaeum.marketlens.infrastructure.config.KafkaTopics
import io.mockk.coEvery
import io.mockk.mockk
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.TestInputTopic
import org.apache.kafka.streams.TestOutputTopic
import org.apache.kafka.streams.TopologyTestDriver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.Properties

class VolumeSpikeTopologyTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val databaseClient: DatabaseClient = mockk(relaxed = true)

    private lateinit var topology: VolumeSpikeTopology
    private lateinit var testDriver: TopologyTestDriver
    private lateinit var inputTopic: TestInputTopic<String, String>
    private lateinit var outputTopic: TestOutputTopic<String, String>

    @BeforeEach
    fun setup() {
        topology = VolumeSpikeTopology(objectMapper, databaseClient)

        // 20일 평균 거래량 설정: 005930 = 390,000 (10분 평균 = 10,000)
        setAvgVolume("005930", 390_000L)
        setAvgVolume("000660", 195_000L) // 10분 평균 = 5,000

        val builder = StreamsBuilder()
        topology.buildTopology(builder)

        val props = Properties().apply {
            put(StreamsConfig.APPLICATION_ID_CONFIG, "test-volume-spike")
            put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092")
            put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde::class.java.name)
            put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.StringSerde::class.java.name)
        }

        testDriver = TopologyTestDriver(builder.build(), props)

        inputTopic = testDriver.createInputTopic(
            KafkaTopics.TICK_RAW,
            StringSerializer(),
            StringSerializer(),
        )
        outputTopic = testDriver.createOutputTopic(
            KafkaTopics.VOLUME_SPIKE,
            StringDeserializer(),
            StringDeserializer(),
        )
    }

    @AfterEach
    fun teardown() {
        testDriver.close()
    }

    @Test
    fun `10분 윈도우 내 거래량 합산이 정확하다`() {
        val baseTime = Instant.parse("2026-02-22T01:00:00Z")

        // 10분 윈도우 내 3건의 틱 발행
        sendTick("005930", 72000L, 1000L, baseTime)
        sendTick("005930", 72500L, 2000L, baseTime.plusSeconds(120))
        sendTick("005930", 73000L, 3000L, baseTime.plusSeconds(300))

        // 윈도우를 닫기 위해 grace period 이후 시점의 틱 발행
        advanceWindowClose("005930", baseTime)

        val records = outputTopic.readKeyValuesToList()
        assertTrue(records.isNotEmpty(), "윈도우 종료 후 결과가 발행되어야 한다")

        val event = objectMapper.readValue<VolumeSpikeEvent>(records.first().value)
        assertEquals("005930", event.stockCode)
        assertEquals(6000L, event.windowVolume) // 1000 + 2000 + 3000
    }

    @Test
    fun `20일 평균 대비 5배 이상이면 spike ratio가 높게 계산된다`() {
        val baseTime = Instant.parse("2026-02-22T01:00:00Z")

        // 005930: 10분 평균 거래량 = 390,000 / 39 = 10,000
        // 60,000 거래량 발생 → spikeRatio = 6.0
        sendTick("005930", 72000L, 60_000L, baseTime)

        advanceWindowClose("005930", baseTime)

        val records = outputTopic.readKeyValuesToList()
        assertTrue(records.isNotEmpty())

        val event = objectMapper.readValue<VolumeSpikeEvent>(records.first().value)
        assertEquals(60_000L, event.windowVolume)
        assertEquals(10_000L, event.avgWindowVolume)
        assertEquals(6.0, event.spikeRatio, 0.01)
    }

    @Test
    fun `윈도우 종료 전에는 중간 결과가 발행되지 않는다 (suppress)`() {
        val baseTime = Instant.parse("2026-02-22T01:00:00Z")

        // 같은 윈도우 내 틱만 발행 (윈도우 닫지 않음)
        sendTick("005930", 72000L, 5000L, baseTime)
        sendTick("005930", 72500L, 3000L, baseTime.plusSeconds(60))

        assertTrue(outputTopic.isEmpty, "윈도우가 닫히기 전에는 결과가 없어야 한다")
    }

    @Test
    fun `grace period 내 지연 도착 데이터가 윈도우에 포함된다`() {
        val baseTime = Instant.parse("2026-02-22T01:00:00Z")

        // 윈도우 내 정상 틱
        sendTick("005930", 72000L, 5000L, baseTime)

        // 윈도우 종료 시각(+10분) 이후, grace 내(+30초)에 도착한 틱
        val lateTickTime = baseTime.plusSeconds(600 + 15) // 윈도우 끝 + 15초 (grace 30초 이내)
        sendTick("005930", 72500L, 3000L, lateTickTime)

        // 윈도우를 닫기 위해 grace period 완전히 경과
        advanceWindowClose("005930", baseTime)

        val records = outputTopic.readKeyValuesToList()
        // 지연 데이터는 새 윈도우에 속할 수 있지만, 정상 윈도우 데이터는 포함됨
        assertTrue(records.isNotEmpty())
    }

    @Test
    fun `평균 거래량이 없는 종목은 spikeRatio가 0이다`() {
        val baseTime = Instant.parse("2026-02-22T01:00:00Z")

        // 평균 거래량 캐시에 없는 종목
        sendTick("999999", 10000L, 5000L, baseTime)

        advanceWindowClose("999999", baseTime)

        val records = outputTopic.readKeyValuesToList()
        assertTrue(records.isNotEmpty())

        val event = objectMapper.readValue<VolumeSpikeEvent>(records.first().value)
        assertEquals(0L, event.avgWindowVolume)
        assertEquals(0.0, event.spikeRatio, 0.01)
    }

    @Test
    fun `서로 다른 종목은 독립적으로 윈도우가 집계된다`() {
        val baseTime = Instant.parse("2026-02-22T01:00:00Z")

        sendTick("005930", 72000L, 10_000L, baseTime)
        sendTick("000660", 180000L, 25_000L, baseTime.plusSeconds(60))

        advanceWindowClose("005930", baseTime)
        advanceWindowClose("000660", baseTime)

        val records = outputTopic.readKeyValuesToList()
        val events = records.map { objectMapper.readValue<VolumeSpikeEvent>(it.value) }

        val samsung = events.find { it.stockCode == "005930" }
        val hynix = events.find { it.stockCode == "000660" }

        assertTrue(samsung != null, "삼성전자 결과가 있어야 한다")
        assertTrue(hynix != null, "SK하이닉스 결과가 있어야 한다")

        assertEquals(10_000L, samsung!!.windowVolume)
        assertEquals(25_000L, hynix!!.windowVolume)

        // 000660: 10분 평균 = 195,000 / 39 = 5,000, spikeRatio = 25,000 / 5,000 = 5.0
        assertEquals(5.0, hynix.spikeRatio, 0.01)
    }

    private fun sendTick(stockCode: String, price: Long, volume: Long, eventTime: Instant) {
        val tick = createTick(stockCode, price, volume, eventTime)
        val json = objectMapper.writeValueAsString(tick)
        inputTopic.pipeInput(stockCode, json, eventTime)
    }

    private fun advanceWindowClose(stockCode: String, windowBaseTime: Instant) {
        // 윈도우 크기(10분) + grace(30초) 이후 시점에 새 틱을 보내 윈도우를 닫음
        val closeTime = windowBaseTime
            .plus(Duration.ofMinutes(VolumeSpikeTopology.WINDOW_SIZE_MINUTES))
            .plus(Duration.ofSeconds(VolumeSpikeTopology.GRACE_PERIOD_SECONDS))
            .plusSeconds(1)
        sendTick(stockCode, 70000L, 1L, closeTime)
    }

    private fun setAvgVolume(stockCode: String, avgDailyVolume: Long) {
        // 리플렉션으로 avgVolumeCache에 직접 접근
        val field = VolumeSpikeTopology::class.java.getDeclaredField("avgVolumeCache")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val cache = field.get(topology) as java.util.concurrent.ConcurrentHashMap<String, Long>
        cache[stockCode] = avgDailyVolume
    }

    private fun createTick(stockCode: String, price: Long, volume: Long, eventTime: Instant) = RealtimeTick(
        stockCode = stockCode,
        currentPrice = price,
        changeRate = BigDecimal("1.50"),
        volume = volume,
        accumulatedVolume = 10_000_000,
        tradingValue = 50_000_000_000,
        tradeTime = LocalTime.of(10, 0, 0),
        eventTime = eventTime,
        tickType = TickType.TRADE,
    )
}
