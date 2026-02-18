package io.kaeum.marketlens.domain.price

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime

data class RealtimeTick(
    val stockCode: String,
    val currentPrice: Long,
    val changeRate: BigDecimal,
    val volume: Long,
    val accumulatedVolume: Long,
    val tradingValue: Long,
    val tradeTime: LocalTime,
    val eventTime: Instant,
    val tickType: TickType,
)

enum class TickType { TRADE, ORDERBOOK }
