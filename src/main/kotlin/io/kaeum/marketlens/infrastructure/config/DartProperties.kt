package io.kaeum.marketlens.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "dart")
data class DartProperties(
    val apiKey: String = "",
    val baseUrl: String = "https://opendart.fss.or.kr/api",
    val connectionTimeoutMs: Long = 5000,
    val readTimeoutMs: Long = 10000,
)
