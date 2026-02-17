package io.kaeum.marketlens.global.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    companion object {
        private const val API_TITLE = "Market-Lens API"
        private const val API_VERSION = "v1"
        private const val API_DESCRIPTION = "K-Stock Real-time Theme Dashboard API"
    }

    @Bean
    fun openApi(): OpenAPI = OpenAPI().info(
        Info()
            .title(API_TITLE)
            .version(API_VERSION)
            .description(API_DESCRIPTION)
    )
}
