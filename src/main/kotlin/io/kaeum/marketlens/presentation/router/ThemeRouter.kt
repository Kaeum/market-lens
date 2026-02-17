package io.kaeum.marketlens.presentation.router

import io.kaeum.marketlens.presentation.handler.ThemeHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class ThemeRouter(private val handler: ThemeHandler) {

    @Bean
    fun themeRoutes() = coRouter {
        "/api/v1/themes".nest {
            GET("", handler::getAllThemes)
            GET("/performance", handler::getThemePerformance)
            GET("/{themeId}/leaders", handler::getThemeLeaders)
        }
    }
}
