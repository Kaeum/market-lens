package io.kaeum.marketlens.domain.stock

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Table("stock_profile")
data class StockProfile(
    @Id val stockCode: String,
    val sector: String?,
    val ceoName: String?,
    val establishedAt: LocalDate?,
    val mainBusiness: String?,
    val per: BigDecimal?,
    val pbr: BigDecimal?,
    val eps: Long?,
    val bps: Long?,
    val dividendYield: BigDecimal?,
    val updatedAt: Instant = Instant.now(),
)
