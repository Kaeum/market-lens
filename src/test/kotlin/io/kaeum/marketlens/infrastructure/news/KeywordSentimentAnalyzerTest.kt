package io.kaeum.marketlens.infrastructure.news

import io.kaeum.marketlens.infrastructure.config.SentimentProperties
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient

class KeywordSentimentAnalyzerTest {

    private lateinit var analyzer: KeywordSentimentAnalyzer
    private val databaseClient: DatabaseClient = mockk()

    private val properties = SentimentProperties(
        keywords = SentimentProperties.Keywords(
            positive = listOf("수주", "흑자전환", "신고가", "호실적", "MOU"),
            negative = listOf("적자", "실적 하향", "횡령", "상장폐지", "소송"),
        )
    )

    @BeforeEach
    fun setUp() {
        analyzer = KeywordSentimentAnalyzer(databaseClient, properties)
    }

    @Test
    fun `analyzeSentiment returns positive for positive keywords`() {
        val score = analyzer.analyzeSentiment("삼성전자 수주 신고가 경신", null)
        assertTrue(score > 0.0, "Expected positive score, got $score")
    }

    @Test
    fun `analyzeSentiment returns negative for negative keywords`() {
        val score = analyzer.analyzeSentiment("A기업 적자 확대 소송 제기", null)
        assertTrue(score < 0.0, "Expected negative score, got $score")
    }

    @Test
    fun `analyzeSentiment returns zero for neutral text`() {
        val score = analyzer.analyzeSentiment("오늘의 날씨는 맑음", null)
        assertEquals(0.0, score)
    }

    @Test
    fun `analyzeSentiment includes summary in analysis`() {
        val score = analyzer.analyzeSentiment("기업 뉴스", "흑자전환 호실적 기록")
        assertTrue(score > 0.0, "Expected positive score from summary, got $score")
    }

    @Test
    fun `analyzeSentiment clamps to valid range`() {
        val score = analyzer.analyzeSentiment(
            "수주 흑자전환 신고가 호실적 MOU",
            "수주 흑자전환 신고가 호실적 MOU"
        )
        assertTrue(score in -1.0..1.0, "Score should be in [-1, 1], got $score")
    }

    @Test
    fun `analyzeSentiment handles mixed sentiment`() {
        // 2 positive (수주, 호실적), 1 negative (적자) → net positive
        val score = analyzer.analyzeSentiment("수주 받았으나 적자 지속, 호실적 기대", null)
        assertTrue(score > 0.0, "Expected net positive score, got $score")
    }

    @Test
    fun `analyzeSentiment returns zero when positive equals negative`() {
        // 1 positive (수주), 1 negative (적자) → zero
        val score = analyzer.analyzeSentiment("수주 받았으나 적자 지속", null)
        assertEquals(0.0, score)
    }
}
