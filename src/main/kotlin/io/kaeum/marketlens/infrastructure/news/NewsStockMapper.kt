package io.kaeum.marketlens.infrastructure.news

import io.kaeum.marketlens.domain.stock.StockRepository
import io.kaeum.marketlens.domain.theme.ThemeRepository
import io.kaeum.marketlens.domain.theme.ThemeStockRepository
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class NewsStockMapper(
    private val stockRepository: StockRepository,
    private val themeRepository: ThemeRepository,
    private val themeStockRepository: ThemeStockRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 종목명 → 종목코드 (ex: "삼성전자" → "005930")
    @Volatile
    private var stockNameToCode: Map<String, String> = emptyMap()

    // 테마명 → 소속 종목코드 목록 (ex: "반도체" → ["005930", "000660", ...])
    @Volatile
    private var themeNameToStockCodes: Map<String, Set<String>> = emptyMap()

    // 테마 키워드 목록 (길이 내림차순 — 긴 키워드부터 매칭하여 부분 매칭 오류 방지)
    @Volatile
    private var themeKeywords: List<String> = emptyList()

    @PostConstruct
    fun init() {
        scope.launch {
            refreshDictionary()
        }
    }

    suspend fun refreshDictionary() {
        try {
            val stocks = stockRepository.findByIsActiveTrue()
            stockNameToCode = stocks.associate { it.stockName to it.stockCode }

            val themes = themeRepository.findAll().toList()
            val themeStockMap = mutableMapOf<String, MutableSet<String>>()
            for (theme in themes) {
                val stockCodes = themeStockRepository.findStockCodesByThemeId(theme.themeId!!)
                // 테마 한글명을 키워드로 사용
                themeStockMap[theme.themeNameKr] = stockCodes.toMutableSet()
            }
            themeNameToStockCodes = themeStockMap
            themeKeywords = themeStockMap.keys.sortedByDescending { it.length }

            log.info(
                "NewsStockMapper dictionary refreshed: {} stocks, {} theme keywords",
                stockNameToCode.size, themeKeywords.size,
            )
        } catch (e: Exception) {
            log.error("Failed to refresh NewsStockMapper dictionary: {}", e.message, e)
        }
    }

    fun mapToStockCodes(title: String, summary: String?): List<StockNewsMapping> {
        val text = buildString {
            append(title)
            if (!summary.isNullOrBlank()) {
                append(" ")
                append(summary)
            }
        }

        val result = mutableMapOf<String, StockNewsMapping>()

        // 1차: 종목명 직접 매칭
        for ((stockName, stockCode) in stockNameToCode) {
            if (stockName.length >= MIN_STOCK_NAME_LENGTH && text.contains(stockName)) {
                result[stockCode] = StockNewsMapping(stockCode, MatchType.DIRECT)
            }
        }

        // 2차: 테마 키워드 매칭 → 소속 종목 전파
        for (keyword in themeKeywords) {
            if (text.contains(keyword)) {
                val stockCodes = themeNameToStockCodes[keyword] ?: continue
                for (stockCode in stockCodes) {
                    // 직접 매칭이 이미 있으면 덮어쓰지 않음
                    result.putIfAbsent(stockCode, StockNewsMapping(stockCode, MatchType.THEME_PROPAGATION))
                }
            }
        }

        return result.values.toList()
    }

    companion object {
        private const val MIN_STOCK_NAME_LENGTH = 2
    }
}

data class StockNewsMapping(
    val stockCode: String,
    val matchType: MatchType,
)

enum class MatchType {
    DIRECT,
    THEME_PROPAGATION,
    AI_CLASSIFIED,
}
