package io.kaeum.marketlens.application.port.`in`

import io.kaeum.marketlens.application.dto.ThemeLeaderResponse
import io.kaeum.marketlens.application.dto.ThemePerformanceResponse
import io.kaeum.marketlens.application.dto.ThemeResponse

interface ThemeQueryUseCase {
    suspend fun getAllThemes(): List<ThemeResponse>
    suspend fun getThemePerformance(sortBy: String, limit: Int): List<ThemePerformanceResponse>
    suspend fun getThemeLeaders(themeId: Long, sortBy: String, limit: Int): ThemeLeaderResponse
}
