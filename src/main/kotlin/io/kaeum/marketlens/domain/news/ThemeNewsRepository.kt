package io.kaeum.marketlens.domain.news

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface ThemeNewsRepository : CoroutineCrudRepository<ThemeNews, Long> {

    @Query("SELECT * FROM theme_news WHERE theme_id = :themeId ORDER BY published_at DESC LIMIT :limit")
    suspend fun findByThemeIdOrderByPublishedAtDesc(themeId: Long, limit: Int): List<ThemeNews>
}
