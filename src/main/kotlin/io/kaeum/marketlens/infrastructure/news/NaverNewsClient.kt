package io.kaeum.marketlens.infrastructure.news

import io.kaeum.marketlens.global.exception.BusinessException
import io.kaeum.marketlens.global.exception.ErrorCode
import io.kaeum.marketlens.infrastructure.config.NaverProperties
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Component
@Profile("!test")
class NaverNewsClient(
    @Qualifier("naverWebClient") private val webClient: WebClient,
    private val naverProperties: NaverProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun searchNews(query: String, display: Int = 100, sort: String = "date"): List<NaverNewsItem> {
        var lastException: Exception? = null

        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                val response = webClient.get()
                    .uri { uriBuilder ->
                        uriBuilder.path("/v1/search/news.json")
                            .queryParam("query", query)
                            .queryParam("display", display)
                            .queryParam("sort", sort)
                            .build()
                    }
                    .header("X-Naver-Client-Id", naverProperties.clientId)
                    .header("X-Naver-Client-Secret", naverProperties.clientSecret)
                    .retrieve()
                    .bodyToMono<NaverSearchResponse>()
                    .awaitSingleOrNull()

                return response?.items?.map { it.toDomain() } ?: emptyList()
            } catch (e: Exception) {
                lastException = e
                log.warn("Naver news search failed for query='{}', attempt {}/{}: {}", query, attempt + 1, MAX_RETRY_ATTEMPTS, e.message)
            }
        }

        log.error("Naver API failed after {} attempts for query='{}': {}", MAX_RETRY_ATTEMPTS, query, lastException?.message)
        throw BusinessException(ErrorCode.NAVER_API_ERROR)
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private val PUB_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
        private val HTML_TAG_REGEX = Regex("<[^>]+>")

        fun stripHtmlTags(text: String): String = HTML_TAG_REGEX.replace(text, "")

        fun parsePubDate(pubDate: String): Instant {
            return try {
                ZonedDateTime.parse(pubDate, PUB_DATE_FORMATTER).toInstant()
            } catch (e: Exception) {
                Instant.now()
            }
        }
    }
}

data class NaverSearchResponse(
    val lastBuildDate: String? = null,
    val total: Int = 0,
    val start: Int = 1,
    val display: Int = 0,
    val items: List<NaverNewsItemDto> = emptyList(),
)

data class NaverNewsItemDto(
    val title: String = "",
    val originallink: String = "",
    val link: String = "",
    val description: String = "",
    val pubDate: String = "",
) {
    fun toDomain(): NaverNewsItem = NaverNewsItem(
        title = NaverNewsClient.stripHtmlTags(title),
        originalLink = originallink,
        description = NaverNewsClient.stripHtmlTags(description),
        publishedAt = NaverNewsClient.parsePubDate(pubDate),
    )
}

data class NaverNewsItem(
    val title: String,
    val originalLink: String,
    val description: String,
    val publishedAt: Instant,
)
