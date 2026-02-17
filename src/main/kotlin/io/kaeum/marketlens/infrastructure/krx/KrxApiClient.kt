package io.kaeum.marketlens.infrastructure.krx

import com.fasterxml.jackson.annotation.JsonProperty
import io.kaeum.marketlens.application.port.out.StockMasterData
import io.kaeum.marketlens.application.port.out.StockMasterPort
import io.kaeum.marketlens.global.exception.BusinessException
import io.kaeum.marketlens.global.exception.ErrorCode
import io.kaeum.marketlens.global.util.withRetry
import io.kaeum.marketlens.infrastructure.config.KrxProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
@Primary
@Profile("!test")
class KrxApiClient(
    @Qualifier("krxWebClient") private val webClient: WebClient,
    private val krxProperties: KrxProperties,
) : StockMasterPort {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val ENDPOINT_STOCK_ISU_BASE_INFO = "/svc/apis/sto/stk_isu_base_info"
        private const val PARAM_AUTH_KEY = "AUTH_KEY"
        private const val PARAM_MKT_TYPE = "MKT_TYPE"
        private const val MARKET_KOSPI = "KOSPI"
        private const val MARKET_KOSDAQ = "KOSDAQ"
        private const val MKT_TYPE_STK = "STK"
        private const val MKT_TYPE_KSQ = "KSQ"
    }

    override suspend fun fetchAllStocks(): List<StockMasterData> {
        return withRetry(maxAttempts = 3, initialDelayMs = 2000) {
            fetchStockList(null)
        }
    }

    override suspend fun fetchStocksByMarket(market: String): List<StockMasterData> {
        return withRetry(maxAttempts = 3, initialDelayMs = 2000) {
            fetchStockList(market)
        }
    }

    private suspend fun fetchStockList(market: String?): List<StockMasterData> {
        val serviceName = when (market?.uppercase()) {
            MARKET_KOSPI -> MKT_TYPE_STK
            MARKET_KOSDAQ -> MKT_TYPE_KSQ
            else -> null
        }

        try {
            val response = webClient.get()
                .uri { builder ->
                    builder.path(ENDPOINT_STOCK_ISU_BASE_INFO)
                        .queryParam(PARAM_AUTH_KEY, krxProperties.apiKey)
                    if (serviceName != null) {
                        builder.queryParam(PARAM_MKT_TYPE, serviceName)
                    }
                    builder.build()
                }
                .retrieve()
                .awaitBody<KrxStockListResponse>()

            return response.output.map { item ->
                StockMasterData(
                    stockCode = item.isuSrtCd,
                    stockName = item.isuNm,
                    market = item.mktName ?: guessMarket(item.isuSrtCd),
                )
            }
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("KRX API call failed: ${e.message}", e)
            throw BusinessException(ErrorCode.KRX_API_ERROR)
        }
    }

    private fun guessMarket(stockCode: String): String {
        // KOSDAQ codes typically start with certain patterns, but this is a fallback
        return MARKET_KOSPI
    }

    private data class KrxStockListResponse(
        @JsonProperty("OutBlock_1") val output: List<KrxStockItem> = emptyList(),
    )

    private data class KrxStockItem(
        @JsonProperty("ISU_SRT_CD") val isuSrtCd: String,
        @JsonProperty("ISU_NM") val isuNm: String,
        @JsonProperty("MKT_NM") val mktName: String?,
    )
}
