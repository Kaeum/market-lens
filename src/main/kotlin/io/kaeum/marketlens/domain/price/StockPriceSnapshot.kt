package io.kaeum.marketlens.domain.price

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant

@Table("stock_price_snapshot")
data class StockPriceSnapshot(
    @Id val stockCode: String,
    val currentPrice: Long,
    val changeRate: BigDecimal?,
    val volume: Long = 0,
    val marketCap: Long?,
    val tradingValue: Long = 0,
    val updatedAt: Instant = Instant.now(),
)
