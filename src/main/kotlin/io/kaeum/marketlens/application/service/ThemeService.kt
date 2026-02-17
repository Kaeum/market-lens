package io.kaeum.marketlens.application.service

import io.kaeum.marketlens.application.dto.*
import io.kaeum.marketlens.application.port.`in`.ThemeQueryUseCase
import io.kaeum.marketlens.domain.price.StockPriceSnapshotRepository
import io.kaeum.marketlens.domain.stock.StockRepository
import io.kaeum.marketlens.domain.theme.ThemeRepository
import io.kaeum.marketlens.domain.theme.ThemeStockRepository
import io.kaeum.marketlens.global.exception.BusinessException
import io.kaeum.marketlens.global.exception.ErrorCode
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class ThemeService(
    private val themeRepository: ThemeRepository,
    private val themeStockRepository: ThemeStockRepository,
    private val stockRepository: StockRepository,
    private val snapshotRepository: StockPriceSnapshotRepository,
) : ThemeQueryUseCase {

    companion object {
        private const val SORT_TOTAL_VOLUME = "total_volume"
        private const val SORT_VOLUME = "volume"
        private const val SORT_MARKET_CAP = "market_cap"
    }

    override suspend fun getAllThemes(): List<ThemeResponse> =
        themeRepository.findAllByOrderByDisplayOrderAsc().map { theme ->
            ThemeResponse(
                themeId = theme.themeId!!,
                themeName = theme.themeName,
                themeNameKr = theme.themeNameKr,
                displayOrder = theme.displayOrder,
                isDynamic = theme.isDynamic,
            )
        }

    override suspend fun getThemePerformance(sortBy: String, limit: Int): List<ThemePerformanceResponse> {
        val themes = themeRepository.findAllByOrderByDisplayOrderAsc()
        val performances = themes.map { theme ->
            val stockCodes = themeStockRepository.findStockCodesByThemeId(theme.themeId!!)
            val snapshots = if (stockCodes.isNotEmpty()) {
                snapshotRepository.findByStockCodeIn(stockCodes)
            } else {
                emptyList()
            }

            val stockMap = stockCodes.associateWith { code ->
                stockRepository.findById(code)
            }

            val avgChangeRate = if (snapshots.isNotEmpty()) {
                snapshots.mapNotNull { it.changeRate }
                    .fold(BigDecimal.ZERO) { acc, rate -> acc + rate }
                    .divide(BigDecimal(snapshots.size), 2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

            val totalVolume = snapshots.sumOf { it.volume }

            val topStocks = snapshots
                .sortedByDescending { it.changeRate ?: BigDecimal.ZERO }
                .take(3)
                .map { snapshot ->
                    val stock = stockMap[snapshot.stockCode]
                    StockSnapshotDto(
                        stockCode = snapshot.stockCode,
                        stockName = stock?.stockName ?: snapshot.stockCode,
                        currentPrice = snapshot.currentPrice,
                        changeRate = snapshot.changeRate,
                        volume = snapshot.volume,
                    )
                }

            ThemePerformanceResponse(
                themeId = theme.themeId,
                themeName = theme.themeName,
                themeNameKr = theme.themeNameKr,
                avgChangeRate = avgChangeRate,
                totalVolume = totalVolume,
                stockCount = stockCodes.size,
                topStocks = topStocks,
            )
        }

        val sorted = when (sortBy) {
            SORT_TOTAL_VOLUME -> performances.sortedByDescending { it.totalVolume }
            else -> performances.sortedByDescending { it.avgChangeRate }
        }

        return sorted.take(limit)
    }

    override suspend fun getThemeLeaders(themeId: Long, sortBy: String, limit: Int): ThemeLeaderResponse {
        val theme = themeRepository.findById(themeId)
            ?: throw BusinessException(ErrorCode.THEME_NOT_FOUND)

        val stockCodes = themeStockRepository.findStockCodesByThemeId(themeId)
        val snapshots = if (stockCodes.isNotEmpty()) {
            snapshotRepository.findByStockCodeIn(stockCodes)
        } else {
            emptyList()
        }

        val sorted = when (sortBy) {
            SORT_VOLUME -> snapshots.sortedByDescending { it.volume }
            SORT_MARKET_CAP -> snapshots.sortedByDescending { it.marketCap ?: 0L }
            else -> snapshots.sortedByDescending { it.changeRate ?: BigDecimal.ZERO }
        }

        val leaders = sorted.take(limit).map { snapshot ->
            val stock = stockRepository.findById(snapshot.stockCode)
            StockSnapshotDto(
                stockCode = snapshot.stockCode,
                stockName = stock?.stockName ?: snapshot.stockCode,
                currentPrice = snapshot.currentPrice,
                changeRate = snapshot.changeRate,
                volume = snapshot.volume,
            )
        }

        return ThemeLeaderResponse(
            themeId = theme.themeId!!,
            themeName = theme.themeName,
            themeNameKr = theme.themeNameKr,
            leaders = leaders,
        )
    }
}
