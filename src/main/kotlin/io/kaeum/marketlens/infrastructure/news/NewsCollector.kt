package io.kaeum.marketlens.infrastructure.news

import io.kaeum.marketlens.infrastructure.config.MarketTimeScheduler
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
@Profile("!test")
class NewsCollector(
    private val naverNewsClient: NaverNewsClient,
    private val dartApiClient: DartApiClient,
    private val newsStockMapper: NewsStockMapper,
    private val keywordSentimentAnalyzer: KeywordSentimentAnalyzer,
    private val databaseClient: DatabaseClient,
    private val marketTimeScheduler: MarketTimeScheduler,
    private val meterRegistry: MeterRegistry,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pollingJob: Job? = null

    private val timer = Timer.builder("collector.news.duration")
        .description("News collection cycle duration")
        .register(meterRegistry)
    private val itemsCounter = meterRegistry.counter("collector.news.items", "source", "total")
    private val naverItemsCounter = meterRegistry.counter("collector.news.items", "source", "naver")
    private val dartItemsCounter = meterRegistry.counter("collector.news.items", "source", "dart")

    @PostConstruct
    fun init() {
        pollingJob = scope.launch {
            // 초기 사전 로드 대기
            delay(INITIAL_DELAY_MS)
            while (isActive) {
                if (!marketTimeScheduler.isTradingDay()) {
                    log.debug("Non-trading day, skipping news polling")
                    delay(NON_TRADING_DAY_CHECK_INTERVAL_MS)
                    continue
                }
                try {
                    val sample = Timer.start(meterRegistry)
                    runPollingCycle()
                    sample.stop(timer)
                } catch (e: Exception) {
                    log.error("News polling cycle failed: {}", e.message, e)
                }
                delay(currentPollingInterval())
            }
        }
        log.info("NewsCollector started")
    }

    @PreDestroy
    fun destroy() {
        pollingJob?.cancel()
        log.info("NewsCollector stopped")
    }

    internal suspend fun runPollingCycle() {
        log.info("Starting news collection cycle")

        var naverCount = 0
        var dartCount = 0

        // 1. 네이버 뉴스 수집 — 테마 키워드 기반 검색
        try {
            naverCount = collectNaverNews()
        } catch (e: Exception) {
            log.warn("Naver news collection failed: {}", e.message)
        }

        // 2. DART 공시 수집
        try {
            dartCount = collectDartDisclosures()
        } catch (e: Exception) {
            log.warn("DART disclosure collection failed: {}", e.message)
        }

        naverItemsCounter.increment(naverCount.toDouble())
        dartItemsCounter.increment(dartCount.toDouble())
        itemsCounter.increment((naverCount + dartCount).toDouble())
        log.info("News collection cycle completed: naver={}, dart={}", naverCount, dartCount)
    }

    private suspend fun collectNaverNews(): Int {
        val keywords = fetchSearchKeywords()
        if (keywords.isEmpty()) {
            log.debug("No search keywords found, skipping Naver news collection")
            return 0
        }

        val allNewsEntries = mutableListOf<NewsEntry>()

        for ((index, keyword) in keywords.withIndex()) {
            if (index > 0) delay(RATE_LIMIT_DELAY_MS)

            try {
                val items = naverNewsClient.searchNews(query = keyword, display = NEWS_PER_KEYWORD)
                for (item in items) {
                    val mappings = newsStockMapper.mapToStockCodes(item.title, item.description)
                    allNewsEntries.add(
                        NewsEntry(
                            title = item.title,
                            summary = item.description,
                            sourceUrl = item.originalLink,
                            publishedAt = item.publishedAt,
                            stockMappings = mappings,
                        )
                    )
                }
            } catch (e: Exception) {
                log.warn("Naver news search failed for keyword='{}': {}", keyword, e.message)
            }
        }

        if (allNewsEntries.isEmpty()) return 0

        return batchInsertNews(allNewsEntries)
    }

    private suspend fun collectDartDisclosures(): Int {
        val today = LocalDate.now(KST)
        val dateStr = today.format(DATE_FORMATTER)

        val disclosures = dartApiClient.fetchTodayDisclosures(beginDate = dateStr, endDate = dateStr)
        if (disclosures.isEmpty()) return 0

        val newsEntries = disclosures.map { disclosure ->
            val mappings = if (!disclosure.stockCode.isNullOrBlank()) {
                listOf(StockNewsMapping(disclosure.stockCode, MatchType.DIRECT))
            } else {
                newsStockMapper.mapToStockCodes(disclosure.reportName, disclosure.corpName)
            }

            NewsEntry(
                title = "[공시] ${disclosure.corpName} - ${disclosure.reportName}",
                summary = "제출인: ${disclosure.filerName}, 비고: ${disclosure.remark}".take(500),
                sourceUrl = disclosure.sourceUrl,
                publishedAt = today.atStartOfDay(KST).toInstant(),
                stockMappings = mappings,
            )
        }

        return batchInsertNews(newsEntries)
    }

    internal suspend fun batchInsertNews(entries: List<NewsEntry>): Int {
        if (entries.isEmpty()) return 0

        var totalInserted = 0
        for (chunk in entries.chunked(BATCH_SIZE)) {
            totalInserted += insertNewsChunk(chunk)
        }
        return totalInserted
    }

    private suspend fun insertNewsChunk(chunk: List<NewsEntry>): Int {
        if (chunk.isEmpty()) return 0

        var insertedCount = 0

        for (entry in chunk) {
            try {
                // theme_news upsert — themeId는 NULL 허용하지 않으므로 매핑된 첫 종목의 테마를 찾거나 0 사용
                // theme_news에 삽입 후 생성된 news_id를 사용하여 stock_news에 매핑
                val themeId = findThemeIdForEntry(entry)
                if (themeId == null) continue

                val sentimentScore = keywordSentimentAnalyzer.analyzeSentiment(entry.title, entry.summary)

                val newsId = databaseClient.sql(
                    """
                    INSERT INTO theme_news (theme_id, title, summary, source_url, published_at, sentiment_score)
                    VALUES (:themeId, :title, :summary, :sourceUrl, :publishedAt, :sentimentScore)
                    ON CONFLICT (source_url) DO NOTHING
                    RETURNING news_id
                    """.trimIndent()
                )
                    .bind("themeId", themeId)
                    .bind("title", entry.title.take(500))
                    .let { spec ->
                        val summary = entry.summary?.take(2000)
                        if (summary != null) spec.bind("summary", summary)
                        else spec.bindNull("summary", String::class.java)
                    }
                    .bind("sourceUrl", entry.sourceUrl.take(1000))
                    .bind("publishedAt", entry.publishedAt)
                    .bind("sentimentScore", java.math.BigDecimal.valueOf(sentimentScore))
                    .map { row, _ -> row.get("news_id", java.lang.Long::class.java)?.toLong() }
                    .first()
                    .awaitFirstOrNull()

                if (newsId != null) {
                    insertedCount++
                    // stock_news 매핑 삽입
                    insertStockNewsMappings(newsId, entry.stockMappings)
                }
            } catch (e: Exception) {
                log.debug("Failed to insert news entry '{}': {}", entry.title.take(50), e.message)
            }
        }

        return insertedCount
    }

    private suspend fun insertStockNewsMappings(newsId: Long, mappings: List<StockNewsMapping>) {
        if (mappings.isEmpty()) return

        for (mapping in mappings) {
            try {
                databaseClient.sql(
                    """
                    INSERT INTO stock_news (news_id, stock_code, match_type)
                    VALUES (:newsId, :stockCode, :matchType)
                    ON CONFLICT (news_id, stock_code) DO NOTHING
                    """.trimIndent()
                )
                    .bind("newsId", newsId)
                    .bind("stockCode", mapping.stockCode)
                    .bind("matchType", mapping.matchType.name)
                    .fetch()
                    .rowsUpdated()
                    .awaitFirstOrNull()
            } catch (e: Exception) {
                log.debug("Failed to insert stock_news mapping newsId={}, stockCode={}: {}", newsId, mapping.stockCode, e.message)
            }
        }
    }

    private suspend fun findThemeIdForEntry(entry: NewsEntry): Long? {
        // 매핑된 종목이 있으면 그 종목이 속한 첫 번째 테마를 찾음
        for (mapping in entry.stockMappings) {
            try {
                val themeId = databaseClient.sql(
                    """
                    SELECT theme_id FROM theme_stock WHERE stock_code = :stockCode LIMIT 1
                    """.trimIndent()
                )
                    .bind("stockCode", mapping.stockCode)
                    .map { row, _ -> row.get("theme_id", java.lang.Long::class.java)?.toLong() }
                    .first()
                    .awaitFirstOrNull()

                if (themeId != null) return themeId
            } catch (_: Exception) {
                // continue to next mapping
            }
        }
        return null
    }

    internal suspend fun fetchSearchKeywords(): List<String> {
        return try {
            // 테마 한글명을 검색 키워드로 사용
            databaseClient.sql("SELECT DISTINCT theme_name_kr FROM theme")
                .map { row, _ -> row.get("theme_name_kr", String::class.java)!! }
                .all()
                .collectList()
                .awaitFirstOrNull() ?: emptyList()
        } catch (e: Exception) {
            log.error("Failed to fetch search keywords: {}", e.message, e)
            emptyList()
        }
    }

    private fun currentPollingInterval(): Long {
        val now = LocalTime.now(KST)
        return when {
            now.isAfter(MARKET_OPEN) && now.isBefore(MARKET_CLOSE) -> POLLING_INTERVAL_MARKET_HOURS_MS
            else -> POLLING_INTERVAL_OFF_HOURS_MS
        }
    }

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val MARKET_OPEN = LocalTime.of(9, 0)
        private val MARKET_CLOSE = LocalTime.of(15, 30)

        private const val INITIAL_DELAY_MS = 10_000L
        private const val NON_TRADING_DAY_CHECK_INTERVAL_MS = 60 * 60 * 1000L // 1시간
        private const val POLLING_INTERVAL_MARKET_HOURS_MS = 5 * 60 * 1000L  // 5분
        private const val POLLING_INTERVAL_OFF_HOURS_MS = 30 * 60 * 1000L    // 30분
        private const val RATE_LIMIT_DELAY_MS = 200L
        private const val BATCH_SIZE = 50
        private const val NEWS_PER_KEYWORD = 20
    }
}

internal data class NewsEntry(
    val title: String,
    val summary: String?,
    val sourceUrl: String,
    val publishedAt: Instant,
    val stockMappings: List<StockNewsMapping>,
)
