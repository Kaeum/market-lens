package io.kaeum.marketlens.infrastructure.kis

import io.kaeum.marketlens.domain.price.RealtimeTick
import io.kaeum.marketlens.domain.price.TickType
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

sealed class KisWebSocketMessage {
    data class Trade(val tick: RealtimeTick) : KisWebSocketMessage()
    data class Heartbeat(val body: String) : KisWebSocketMessage()
    data class SubscriptionResponse(
        val trId: String,
        val stockCode: String,
        val success: Boolean,
    ) : KisWebSocketMessage()
    data class Unknown(val raw: String) : KisWebSocketMessage()
}

object KisWebSocketMessageParser {

    private val log = LoggerFactory.getLogger(javaClass)
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss")

    private const val TR_ID_TRADE = "H0STCNT0"
    private const val FIELD_SEPARATOR = "^"
    private const val HEADER_SEPARATOR = "|"

    // H0STCNT0 체결가 필드 인덱스
    private const val IDX_STOCK_CODE = 0
    private const val IDX_TRADE_TIME = 1
    private const val IDX_CURRENT_PRICE = 2
    private const val IDX_CHANGE_RATE = 5
    private const val IDX_VOLUME = 9
    private const val IDX_ACCUMULATED_VOLUME = 12
    private const val IDX_TRADING_VALUE = 14
    private const val MIN_TRADE_FIELDS = 15

    fun parse(rawMessage: String): KisWebSocketMessage {
        if (rawMessage.isBlank()) {
            return KisWebSocketMessage.Unknown(rawMessage)
        }

        // JSON 형식 — 구독 응답 또는 PINGPONG
        if (rawMessage.trimStart().startsWith("{")) {
            return parseJsonMessage(rawMessage)
        }

        // 파이프 구분 데이터 메시지: encrypted|trId|dataCount|data
        return parsePipeMessage(rawMessage)
    }

    private fun parseJsonMessage(rawMessage: String): KisWebSocketMessage {
        try {
            // PINGPONG heartbeat 감지
            if (rawMessage.contains("PINGPONG")) {
                return KisWebSocketMessage.Heartbeat(rawMessage)
            }

            // 구독 응답 파싱 — 간단한 문자열 기반 파싱 (Jackson 의존 제거)
            val trId = extractJsonField(rawMessage, "tr_id") ?: ""
            val trKey = extractJsonField(rawMessage, "tr_key") ?: ""
            val msgCd = extractJsonField(rawMessage, "msg_cd") ?: ""

            if (trId.isNotEmpty()) {
                val success = msgCd == "OPSP0000" || msgCd == "OPSP0001"
                return KisWebSocketMessage.SubscriptionResponse(
                    trId = trId,
                    stockCode = trKey,
                    success = success,
                )
            }
        } catch (e: Exception) {
            log.warn("Failed to parse JSON message: {}", e.message)
        }
        return KisWebSocketMessage.Unknown(rawMessage)
    }

    private fun extractJsonField(json: String, fieldName: String): String? {
        val pattern = "\"$fieldName\"\\s*:\\s*\"([^\"]*)\""
        val regex = Regex(pattern)
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun parsePipeMessage(rawMessage: String): KisWebSocketMessage {
        val headerParts = rawMessage.split(HEADER_SEPARATOR)
        if (headerParts.size < 4) {
            return KisWebSocketMessage.Unknown(rawMessage)
        }

        val trId = headerParts[1]
        val dataBody = headerParts[3]

        if (trId != TR_ID_TRADE) {
            return KisWebSocketMessage.Unknown(rawMessage)
        }

        return parseTradeData(dataBody)
    }

    private fun parseTradeData(dataBody: String): KisWebSocketMessage {
        val fields = dataBody.split(FIELD_SEPARATOR)
        if (fields.size <= MIN_TRADE_FIELDS) {
            log.warn("Trade data has insufficient fields: {}", fields.size)
            return KisWebSocketMessage.Unknown(dataBody)
        }

        try {
            val tradeTimeStr = fields[IDX_TRADE_TIME]
            val tradeTime = LocalTime.parse(tradeTimeStr, TIME_FORMATTER)

            val tick = RealtimeTick(
                stockCode = fields[IDX_STOCK_CODE],
                currentPrice = fields[IDX_CURRENT_PRICE].toLong(),
                changeRate = BigDecimal(fields[IDX_CHANGE_RATE]),
                volume = fields[IDX_VOLUME].toLong(),
                accumulatedVolume = fields[IDX_ACCUMULATED_VOLUME].toLong(),
                tradingValue = fields[IDX_TRADING_VALUE].toLong(),
                tradeTime = tradeTime,
                eventTime = Instant.now(),
                tickType = TickType.TRADE,
            )
            return KisWebSocketMessage.Trade(tick)
        } catch (e: Exception) {
            log.warn("Failed to parse trade data: {}", e.message)
            return KisWebSocketMessage.Unknown(dataBody)
        }
    }
}
