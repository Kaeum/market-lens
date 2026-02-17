package io.kaeum.marketlens.domain.theme

import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("theme_stock")
data class ThemeStock(
    val themeId: Long,
    val stockCode: String,
    val createdAt: Instant = Instant.now(),
)
