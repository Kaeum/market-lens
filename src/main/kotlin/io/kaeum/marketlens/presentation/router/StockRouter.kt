package io.kaeum.marketlens.presentation.router

import io.kaeum.marketlens.presentation.handler.StockHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class StockRouter(private val handler: StockHandler) {

    companion object {
        private const val BASE_PATH = "/api/v1/stocks"
        private const val PATH_DETAIL = "/{stockCode}"
    }

    @Bean
    fun stockRoutes() = coRouter {
        BASE_PATH.nest {
            GET("", handler::searchStocks)
            GET(PATH_DETAIL, handler::getStockDetail)
        }
    }
}
