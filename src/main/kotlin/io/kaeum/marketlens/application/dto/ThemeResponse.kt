package io.kaeum.marketlens.application.dto

data class ThemeResponse(
    val themeId: Long,
    val themeName: String,
    val themeNameKr: String,
    val displayOrder: Int,
    val isDynamic: Boolean,
)
