package io.kaeum.marketlens.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig {

    companion object {
        private const val ALL_PATTERN = "*"
        private const val ALL_PATHS = "/**"
    }

    @Bean
    fun corsWebFilter(): CorsWebFilter {
        val config = CorsConfiguration().apply {
            addAllowedOriginPattern(ALL_PATTERN)
            addAllowedMethod(ALL_PATTERN)
            addAllowedHeader(ALL_PATTERN)
            allowCredentials = true
        }
        val source = UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration(ALL_PATHS, config)
        }
        return CorsWebFilter(source)
    }
}
