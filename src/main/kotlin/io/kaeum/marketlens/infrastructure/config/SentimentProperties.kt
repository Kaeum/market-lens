package io.kaeum.marketlens.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "sentiment")
data class SentimentProperties(
    val keywords: Keywords = Keywords(),
) {
    data class Keywords(
        val positive: List<String> = emptyList(),
        val negative: List<String> = emptyList(),
    )
}
