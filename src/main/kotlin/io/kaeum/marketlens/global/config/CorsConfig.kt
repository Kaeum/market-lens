package io.kaeum.marketlens.global.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@ConfigurationProperties(prefix = "cors")
data class CorsProperties(
    val allowedOrigins: List<String> = listOf("http://localhost:3000"),
)

@Configuration
@EnableConfigurationProperties(CorsProperties::class)
class CorsConfig(
    private val corsProperties: CorsProperties,
) {

    companion object {
        private const val ALL_PATTERN = "*"
        private const val ALL_PATHS = "/**"
    }

    @Bean
    fun corsWebFilter(): CorsWebFilter {
        val config = CorsConfiguration().apply {
            corsProperties.allowedOrigins.forEach { addAllowedOrigin(it) }
            addAllowedMethod(ALL_PATTERN)
            addAllowedHeader(ALL_PATTERN)
            allowCredentials = false
        }
        val source = UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration(ALL_PATHS, config)
        }
        return CorsWebFilter(source)
    }
}
