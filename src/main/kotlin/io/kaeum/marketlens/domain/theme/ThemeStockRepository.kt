package io.kaeum.marketlens.domain.theme

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface ThemeStockRepository : CoroutineCrudRepository<ThemeStock, Long> {

    suspend fun findByThemeId(themeId: Long): List<ThemeStock>

    @Query("SELECT stock_code FROM theme_stock WHERE theme_id = :themeId")
    suspend fun findStockCodesByThemeId(themeId: Long): List<String>
}
