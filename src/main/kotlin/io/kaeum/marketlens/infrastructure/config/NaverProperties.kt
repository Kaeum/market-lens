package io.kaeum.marketlens.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "naver")
data class NaverProperties(
    val clientId: String = "",
    val clientSecret: String = "",
    val baseUrl: String = "https://openapi.naver.com",
    val connectionTimeoutMs: Long = 5000,
    val readTimeoutMs: Long = 10000,
)
