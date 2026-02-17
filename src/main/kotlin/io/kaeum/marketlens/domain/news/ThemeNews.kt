package io.kaeum.marketlens.domain.news

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("theme_news")
data class ThemeNews(
    @Id val newsId: Long? = null,
    val themeId: Long,
    val title: String,
    val summary: String?,
    val sourceUrl: String,
    val publishedAt: Instant,
    val createdAt: Instant = Instant.now(),
)
