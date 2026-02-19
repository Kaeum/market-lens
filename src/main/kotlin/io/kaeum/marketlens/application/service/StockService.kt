package io.kaeum.marketlens.application.service

import io.kaeum.marketlens.application.dto.StockResponse
import io.kaeum.marketlens.application.port.`in`.StockQueryUseCase
import io.kaeum.marketlens.domain.stock.StockRepository
import io.kaeum.marketlens.global.exception.BusinessException
import io.kaeum.marketlens.global.exception.ErrorCode
import io.kaeum.marketlens.infrastructure.redis.SnapshotReader
import org.springframework.stereotype.Service

@Service
class StockService(
    private val stockRepository: StockRepository,
    private val snapshotReader: SnapshotReader,
) : StockQueryUseCase {

    override suspend fun searchStocks(market: String?, keyword: String?): List<StockResponse> {
        val stocks = when {
            keyword != null -> stockRepository.searchByKeyword("%$keyword%")
            market != null -> stockRepository.findByMarketAndIsActiveTrue(market)
            else -> stockRepository.findByIsActiveTrue()
        }

        val stockCodes = stocks.map { it.stockCode }
        val snapshots = if (stockCodes.isNotEmpty()) {
            snapshotReader.findByStockCodeIn(stockCodes).associateBy { it.stockCode }
        } else {
            emptyMap()
        }

        return stocks.map { stock ->
            val snapshot = snapshots[stock.stockCode]
            StockResponse(
                stockCode = stock.stockCode,
                stockName = stock.stockName,
                market = stock.market,
                currentPrice = snapshot?.currentPrice,
                changeRate = snapshot?.changeRate,
                volume = snapshot?.volume,
                marketCap = snapshot?.marketCap,
            )
        }
    }

    override suspend fun getStockDetail(stockCode: String): StockResponse {
        val stock = stockRepository.findById(stockCode)
            ?: throw BusinessException(ErrorCode.STOCK_NOT_FOUND)

        val snapshot = snapshotReader.findById(stockCode)

        return StockResponse(
            stockCode = stock.stockCode,
            stockName = stock.stockName,
            market = stock.market,
            currentPrice = snapshot?.currentPrice,
            changeRate = snapshot?.changeRate,
            volume = snapshot?.volume,
            marketCap = snapshot?.marketCap,
        )
    }
}
