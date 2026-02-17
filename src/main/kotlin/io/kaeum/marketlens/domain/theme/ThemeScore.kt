package io.kaeum.marketlens.domain.theme

import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.Instant

@Table("theme_score")
data class ThemeScore(
    val time: Instant,
    val themeId: Long,
    val score: BigDecimal,
    val avgChangeRate: BigDecimal?,
    val totalVolume: Long = 0,
    val tradingValue: Long = 0,
    val cohesion: BigDecimal = BigDecimal.ZERO,
    val stockCount: Int = 0,
)
