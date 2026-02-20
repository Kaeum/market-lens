package io.kaeum.marketlens.domain.news

import org.springframework.data.relational.core.mapping.Table

@Table("stock_news")
data class StockNews(
    val newsId: Long,
    val stockCode: String,
    val matchType: String,
)
