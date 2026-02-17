package io.kaeum.marketlens.domain.theme

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("theme")
data class Theme(
    @Id val themeId: Long? = null,
    val themeName: String,
    val themeNameKr: String,
    val displayOrder: Int = 0,
    val isDynamic: Boolean = false,
    val createdAt: Instant = Instant.now(),
)
