package io.kaeum.marketlens.application.service

import io.kaeum.marketlens.domain.price.StockPriceSnapshot
import io.kaeum.marketlens.domain.price.StockPriceSnapshotRepository
import io.kaeum.marketlens.domain.stock.Stock
import io.kaeum.marketlens.domain.stock.StockRepository
import io.kaeum.marketlens.domain.theme.Theme
import io.kaeum.marketlens.domain.theme.ThemeRepository
import io.kaeum.marketlens.domain.theme.ThemeStockRepository
import io.kaeum.marketlens.global.exception.BusinessException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ThemeServiceTest {

    private lateinit var themeRepository: ThemeRepository
    private lateinit var themeStockRepository: ThemeStockRepository
    private lateinit var stockRepository: StockRepository
    private lateinit var snapshotRepository: StockPriceSnapshotRepository
    private lateinit var themeService: ThemeService

    @BeforeEach
    fun setUp() {
        themeRepository = mockk()
        themeStockRepository = mockk()
        stockRepository = mockk()
        snapshotRepository = mockk()
        themeService = ThemeService(themeRepository, themeStockRepository, stockRepository, snapshotRepository)
    }

    @Test
    fun `getAllThemes returns all themes ordered by displayOrder`() = runTest {
        val themes = listOf(
            Theme(themeId = 1, themeName = "semiconductor", themeNameKr = "반도체", displayOrder = 1),
            Theme(themeId = 2, themeName = "battery", themeNameKr = "2차전지", displayOrder = 2),
        )
        coEvery { themeRepository.findAllByOrderByDisplayOrderAsc() } returns themes

        val result = themeService.getAllThemes()

        assertEquals(2, result.size)
        assertEquals("반도체", result[0].themeNameKr)
        assertEquals("2차전지", result[1].themeNameKr)
    }

    @Test
    fun `getThemeLeaders throws when theme not found`() = runTest {
        coEvery { themeRepository.findById(999L) } returns null

        assertThrows(BusinessException::class.java) {
            kotlinx.coroutines.test.runTest {
                themeService.getThemeLeaders(999L, "change_rate", 10)
            }
        }
    }

    @Test
    fun `getThemeLeaders returns leaders sorted by change rate`() = runTest {
        val theme = Theme(themeId = 1, themeName = "semiconductor", themeNameKr = "반도체", displayOrder = 1)
        coEvery { themeRepository.findById(1L) } returns theme
        coEvery { themeStockRepository.findStockCodesByThemeId(1L) } returns listOf("005930", "000660")
        coEvery { snapshotRepository.findByStockCodeIn(listOf("005930", "000660")) } returns listOf(
            StockPriceSnapshot("005930", 72000, BigDecimal("1.50"), 10000000, 430000000000000),
            StockPriceSnapshot("000660", 180000, BigDecimal("3.20"), 5000000, 130000000000000),
        )
        coEvery { stockRepository.findById("000660") } returns Stock("000660", "SK하이닉스", "KOSPI")
        coEvery { stockRepository.findById("005930") } returns Stock("005930", "삼성전자", "KOSPI")

        val result = themeService.getThemeLeaders(1L, "change_rate", 10)

        assertEquals("반도체", result.themeNameKr)
        assertEquals(2, result.leaders.size)
        assertEquals("SK하이닉스", result.leaders[0].stockName)
        assertEquals("삼성전자", result.leaders[1].stockName)
    }

    @Test
    fun `getThemePerformance calculates avg change rate correctly`() = runTest {
        val themes = listOf(
            Theme(themeId = 1, themeName = "semiconductor", themeNameKr = "반도체", displayOrder = 1),
        )
        coEvery { themeRepository.findAllByOrderByDisplayOrderAsc() } returns themes
        coEvery { themeStockRepository.findStockCodesByThemeId(1L) } returns listOf("005930", "000660")
        coEvery { snapshotRepository.findByStockCodeIn(listOf("005930", "000660")) } returns listOf(
            StockPriceSnapshot("005930", 72000, BigDecimal("2.00"), 10000000, null),
            StockPriceSnapshot("000660", 180000, BigDecimal("4.00"), 5000000, null),
        )
        coEvery { stockRepository.findById("005930") } returns Stock("005930", "삼성전자", "KOSPI")
        coEvery { stockRepository.findById("000660") } returns Stock("000660", "SK하이닉스", "KOSPI")

        val result = themeService.getThemePerformance("avg_change_rate", 15)

        assertEquals(1, result.size)
        assertEquals(BigDecimal("3.00"), result[0].avgChangeRate)
        assertEquals(15000000L, result[0].totalVolume)
    }
}
