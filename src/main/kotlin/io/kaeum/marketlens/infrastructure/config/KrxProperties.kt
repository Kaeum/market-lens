package io.kaeum.marketlens.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "krx")
data class KrxProperties(
    val apiKey: String = "",
    val baseUrl: String = "http://data-dbg.krx.co.kr/svc/apis",
    val connectionTimeoutMs: Long = 10000,
    val readTimeoutMs: Long = 30000,
)
