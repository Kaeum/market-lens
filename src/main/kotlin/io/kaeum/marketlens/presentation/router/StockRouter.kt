package io.kaeum.marketlens.presentation.router

import io.kaeum.marketlens.presentation.handler.StockHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class StockRouter(private val handler: StockHandler) {

    @Bean
    fun stockRoutes() = coRouter {
        "/api/v1/stocks".nest {
            GET("", handler::searchStocks)
            GET("/{stockCode}", handler::getStockDetail)
        }
    }
}
