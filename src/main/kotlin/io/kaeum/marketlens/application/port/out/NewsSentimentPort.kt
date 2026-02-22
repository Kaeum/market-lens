package io.kaeum.marketlens.application.port.out

interface NewsSentimentPort {
    suspend fun getSentiment(stockCode: String, windowMinutes: Int): Double
}
