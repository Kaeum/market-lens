package io.kaeum.marketlens.domain.stock

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("stock")
data class Stock(
    @Id val stockCode: String,
    val stockName: String,
    val market: String,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
