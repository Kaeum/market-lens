package io.kaeum.marketlens.infrastructure.kis

import com.fasterxml.jackson.annotation.JsonProperty
import io.kaeum.marketlens.application.port.out.StockPricePort
import io.kaeum.marketlens.domain.price.StockPriceSnapshot
import io.kaeum.marketlens.global.exception.BusinessException
import io.kaeum.marketlens.global.exception.ErrorCode
import io.kaeum.marketlens.global.util.withRetry
import io.kaeum.marketlens.infrastructure.config.KisProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchange
import java.math.BigDecimal
import java.time.Instant

@Component
@Profile("!test")
class KisApiClient(
    @Qualifier("kisWebClient") private val webClient: WebClient,
    private val kisProperties: KisProperties,
    private val tokenManager: KisTokenManager,
) : StockPricePort {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val ENDPOINT_CURRENT_PRICE = "/uapi/domestic-stock/v1/quotations/inquire-price"
        private const val HEADER_AUTHORIZATION = "authorization"
        private const val HEADER_APPKEY = "appkey"
        private const val HEADER_APPSECRET = "appsecret"
        private const val HEADER_TR_ID = "tr_id"
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
        private const val TR_ID_CURRENT_PRICE = "FHKST01010100"
        private const val PARAM_MARKET_DIV_CODE = "FID_COND_MRKT_DIV_CODE"
        private const val PARAM_INPUT_ISCD = "FID_INPUT_ISCD"
        private const val MARKET_DIV_CODE_ALL = "J"
        private const val RESPONSE_SUCCESS_CODE = "0"
        private const val RATE_LIMIT_DELAY_MS = 100L
    }

    suspend fun getCurrentPrice(stockCode: String): StockPriceSnapshot {
        return withRetry(maxAttempts = 3, initialDelayMs = 1000) {
            val token = tokenManager.getAccessToken()

            val response = webClient.get()
                .uri { builder ->
                    builder.path(ENDPOINT_CURRENT_PRICE)
                        .queryParam(PARAM_MARKET_DIV_CODE, MARKET_DIV_CODE_ALL)
                        .queryParam(PARAM_INPUT_ISCD, stockCode)
                        .build()
                }
                .header(HEADER_AUTHORIZATION, "Bearer $token")
                .header(HEADER_APPKEY, kisProperties.appKey)
                .header(HEADER_APPSECRET, kisProperties.appSecret)
                .header(HEADER_TR_ID, TR_ID_CURRENT_PRICE)
                .header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
                .awaitExchange { clientResponse ->
                    if (clientResponse.statusCode() == HttpStatusCode.valueOf(401)) {
                        // Token expired — force refresh and rethrow to trigger retry
                        tokenManager.getAccessToken()
                        throw BusinessException(ErrorCode.KIS_TOKEN_ERROR)
                    }
                    if (clientResponse.statusCode().isError) {
                        throw BusinessException(ErrorCode.KIS_API_ERROR)
                    }
                    clientResponse.awaitBody<KisCurrentPriceResponse>()
                }

            if (response.rtCd != RESPONSE_SUCCESS_CODE) {
                log.error("KIS API error: code={}, message={}", response.msgCd, response.msg1)
                throw BusinessException(ErrorCode.KIS_API_ERROR)
            }

            val output = response.output
                ?: throw BusinessException(ErrorCode.KIS_API_ERROR)

            output.toSnapshot(stockCode)
        }
    }

    override suspend fun getLatestPrices(stockCodes: List<String>): List<StockPriceSnapshot> {
        return stockCodes.mapIndexed { index, stockCode ->
            if (index > 0) {
                delay(RATE_LIMIT_DELAY_MS)
            }
            try {
                getCurrentPrice(stockCode)
            } catch (e: Exception) {
                log.warn("Failed to fetch price for $stockCode: ${e.message}")
                null
            }
        }.filterNotNull()
    }

    override fun priceStream(stockCode: String): Flow<StockPriceSnapshot> {
        // Phase 2-2에서 WebSocket 기반 구현 예정
        return emptyFlow()
    }

    private data class KisCurrentPriceResponse(
        @JsonProperty("rt_cd") val rtCd: String,
        @JsonProperty("msg_cd") val msgCd: String,
        @JsonProperty("msg1") val msg1: String,
        @JsonProperty("output") val output: KisCurrentPriceOutput?,
    )

    private data class KisCurrentPriceOutput(
        @JsonProperty("stck_prpr") val currentPrice: String,
        @JsonProperty("prdy_ctrt") val changeRate: String,
        @JsonProperty("acml_vol") val volume: String,
        @JsonProperty("hts_avls") val marketCap: String,
        @JsonProperty("acml_tr_pbmn") val tradingValue: String,
    ) {
        fun toSnapshot(stockCode: String) = StockPriceSnapshot(
            stockCode = stockCode,
            currentPrice = currentPrice.toLongOrNull() ?: 0L,
            changeRate = changeRate.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            volume = volume.toLongOrNull() ?: 0L,
            marketCap = marketCap.toLongOrNull(),
            tradingValue = tradingValue.toLongOrNull() ?: 0L,
            updatedAt = Instant.now(),
        )
    }
}
