package io.kaeum.marketlens.presentation.router

import io.kaeum.marketlens.presentation.handler.ThemeHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.coRouter

@Configuration
class ThemeRouter(private val handler: ThemeHandler) {

    companion object {
        private const val BASE_PATH = "/api/v1/themes"
        private const val PATH_PERFORMANCE = "/performance"
        private const val PATH_LEADERS = "/{themeId}/leaders"
    }

    @Bean
    fun themeRoutes() = coRouter {
        BASE_PATH.nest {
            GET("", handler::getAllThemes)
            GET(PATH_PERFORMANCE, handler::getThemePerformance)
            GET(PATH_LEADERS, handler::getThemeLeaders)
        }
    }
}
