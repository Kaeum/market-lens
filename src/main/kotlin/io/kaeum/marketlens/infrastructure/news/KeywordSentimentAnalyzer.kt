package io.kaeum.marketlens.infrastructure.news

import io.kaeum.marketlens.application.port.out.NewsSentimentPort
import io.kaeum.marketlens.infrastructure.config.SentimentProperties
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
@EnableConfigurationProperties(SentimentProperties::class)
class KeywordSentimentAnalyzer(
    private val databaseClient: DatabaseClient,
    private val sentimentProperties: SentimentProperties,
) : NewsSentimentPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun getSentiment(stockCode: String, windowMinutes: Int): Double {
        val since = Instant.now().minus(windowMinutes.toLong(), ChronoUnit.MINUTES)
        val newsList = fetchNewsForStock(stockCode, since)

        if (newsList.isEmpty()) return 0.0

        val positiveKeywords = sentimentProperties.keywords.positive
        val negativeKeywords = sentimentProperties.keywords.negative

        var positiveMatches = 0
        var negativeMatches = 0

        for (news in newsList) {
            val text = "${news.title} ${news.summary ?: ""}"
            positiveMatches += positiveKeywords.count { text.contains(it) }
            negativeMatches += negativeKeywords.count { text.contains(it) }
        }

        val sentimentRaw = (positiveMatches - negativeMatches).toDouble() / newsList.size

        // frequency bonus: 최근 1시간 뉴스 5건 이상이면 1.2배
        val recentSince = Instant.now().minus(1, ChronoUnit.HOURS)
        val recentCount = newsList.count { it.publishedAt.isAfter(recentSince) }
        val frequencyBonus = if (recentCount >= FREQUENCY_THRESHOLD) FREQUENCY_BONUS else 1.0

        return (sentimentRaw * frequencyBonus).coerceIn(-1.0, 1.0)
    }

    fun analyzeSentiment(title: String, summary: String?): Double {
        val text = "$title ${summary ?: ""}"
        val positiveKeywords = sentimentProperties.keywords.positive
        val negativeKeywords = sentimentProperties.keywords.negative

        val positiveCount = positiveKeywords.count { text.contains(it) }
        val negativeCount = negativeKeywords.count { text.contains(it) }

        val total = positiveCount + negativeCount
        if (total == 0) return 0.0

        return ((positiveCount - negativeCount).toDouble() / total).coerceIn(-1.0, 1.0)
    }

    private suspend fun fetchNewsForStock(stockCode: String, since: Instant): List<NewsRecord> {
        return try {
            databaseClient.sql(
                """
                SELECT tn.title, tn.summary, tn.published_at
                FROM theme_news tn
                JOIN stock_news sn ON sn.news_id = tn.news_id
                WHERE sn.stock_code = :stockCode
                  AND tn.published_at >= :since
                ORDER BY tn.published_at DESC
                """.trimIndent()
            )
                .bind("stockCode", stockCode)
                .bind("since", since)
                .map { row, _ ->
                    NewsRecord(
                        title = row.get("title", String::class.java) ?: "",
                        summary = row.get("summary", String::class.java),
                        publishedAt = row.get("published_at", Instant::class.java) ?: Instant.now(),
                    )
                }
                .all()
                .collectList()
                .awaitFirstOrNull() ?: emptyList()
        } catch (e: Exception) {
            log.warn("Failed to fetch news for stock {}: {}", stockCode, e.message)
            emptyList()
        }
    }

    private data class NewsRecord(
        val title: String,
        val summary: String?,
        val publishedAt: Instant,
    )

    companion object {
        private const val FREQUENCY_THRESHOLD = 5
        private const val FREQUENCY_BONUS = 1.2
    }
}
