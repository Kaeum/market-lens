package io.kaeum.marketlens.presentation.handler

import io.kaeum.marketlens.application.port.`in`.StockQueryUseCase
import io.kaeum.marketlens.global.common.ApiResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class StockHandler(private val stockQueryUseCase: StockQueryUseCase) {

    companion object {
        private const val PARAM_MARKET = "market"
        private const val PARAM_SEARCH = "search"
        private const val PATH_VAR_STOCK_CODE = "stockCode"
    }

    suspend fun searchStocks(request: ServerRequest): ServerResponse {
        val market = request.queryParam(PARAM_MARKET).orElse(null)
        val keyword = request.queryParam(PARAM_SEARCH).orElse(null)
        val stocks = stockQueryUseCase.searchStocks(market, keyword)
        return ServerResponse.ok().bodyValueAndAwait(ApiResponse.ok(stocks))
    }

    suspend fun getStockDetail(request: ServerRequest): ServerResponse {
        val stockCode = request.pathVariable(PATH_VAR_STOCK_CODE)
        val stock = stockQueryUseCase.getStockDetail(stockCode)
        return ServerResponse.ok().bodyValueAndAwait(ApiResponse.ok(stock))
    }
}
