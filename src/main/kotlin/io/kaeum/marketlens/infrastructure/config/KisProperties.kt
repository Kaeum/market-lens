package io.kaeum.marketlens.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kis")
data class KisProperties(
    val appKey: String = "",
    val appSecret: String = "",
    val accountNumber: String = "",
    val baseUrl: String = "https://openapivts.koreainvestment.com:29443",
    val tokenRefreshBeforeMinutes: Long = 30,
    val connectionTimeoutMs: Long = 5000,
    val readTimeoutMs: Long = 10000,
    val wsUrl: String = "ws://ops.koreainvestment.com:21000",
    val wsMaxSubscriptions: Int = 40,
    val wsReconnectInitialDelayMs: Long = 1000,
    val wsReconnectMaxDelayMs: Long = 60000,
    val wsHeartbeatTimeoutMs: Long = 60000,
)
