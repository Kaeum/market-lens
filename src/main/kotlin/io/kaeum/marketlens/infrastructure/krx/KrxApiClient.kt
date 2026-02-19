package io.kaeum.marketlens.infrastructure.krx

import com.fasterxml.jackson.annotation.JsonProperty
import io.kaeum.marketlens.application.port.out.StockMasterData
import io.kaeum.marketlens.application.port.out.StockMasterPort
import io.kaeum.marketlens.domain.price.StockDailyPrice
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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
        private const val ENDPOINT_KOSPI_DAILY = "/svc/apis/sto/stk_bdd_trd"
        private const val ENDPOINT_KOSDAQ_DAILY = "/svc/apis/sto/ksq_bdd_trd"
        private const val PARAM_AUTH_KEY = "AUTH_KEY"
        private const val PARAM_MKT_TYPE = "MKT_TYPE"
        private const val PARAM_BAS_DD = "BAS_DD"
        private const val MARKET_KOSPI = "KOSPI"
        private const val MARKET_KOSDAQ = "KOSDAQ"
        private const val MKT_TYPE_STK = "STK"
        private const val MKT_TYPE_KSQ = "KSQ"
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")
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
        // KOSDAQ 종목코드는 일반적으로 '2', '3', '4', '9'로 시작하거나 'A'로 시작하는 6자리 코드
        // KOSPI 종목코드는 '0', '1', '5'로 시작하는 경향
        // 이 로직은 MKT_NM 필드가 null인 경우의 폴백이므로, 완벽하지 않을 수 있음
        val firstChar = stockCode.firstOrNull() ?: return MARKET_KOSPI
        return when (firstChar) {
            '2', '3', '4', '9' -> MARKET_KOSDAQ
            else -> MARKET_KOSPI
        }
    }

    suspend fun fetchDailyOhlcv(date: LocalDate, market: String): List<StockDailyPrice> {
        return withRetry(maxAttempts = 3, initialDelayMs = 2000) {
            fetchOhlcv(date, market)
        }
    }

    private suspend fun fetchOhlcv(date: LocalDate, market: String): List<StockDailyPrice> {
        val endpoint = when (market.uppercase()) {
            MARKET_KOSPI -> ENDPOINT_KOSPI_DAILY
            MARKET_KOSDAQ -> ENDPOINT_KOSDAQ_DAILY
            else -> throw BusinessException(ErrorCode.INVALID_INPUT)
        }

        try {
            val response = webClient.get()
                .uri { builder ->
                    builder.path(endpoint)
                        .queryParam(PARAM_AUTH_KEY, krxProperties.apiKey)
                        .queryParam(PARAM_BAS_DD, date.format(DATE_FORMATTER))
                        .build()
                }
                .retrieve()
                .awaitBody<KrxOhlcvResponse>()

            return response.output.mapNotNull { item -> item.toStockDailyPrice(date) }
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("KRX OHLCV API call failed for date={}, market={}: {}", date, market, e.message, e)
            throw BusinessException(ErrorCode.KRX_API_ERROR)
        }
    }

    private data class KrxStockListResponse(
        @JsonProperty("OutBlock_1") val output: List<KrxStockItem> = emptyList(),
    )

    private data class KrxStockItem(
        @JsonProperty("ISU_SRT_CD") val isuSrtCd: String,
        @JsonProperty("ISU_NM") val isuNm: String,
        @JsonProperty("MKT_NM") val mktName: String?,
    )

    private data class KrxOhlcvResponse(
        @JsonProperty("OutBlock_1") val output: List<KrxOhlcvItem> = emptyList(),
    )

    private data class KrxOhlcvItem(
        @JsonProperty("ISU_SRT_CD") val stockCode: String,
        @JsonProperty("TDD_OPNPRC") val openPrice: String?,
        @JsonProperty("TDD_HGPRC") val highPrice: String?,
        @JsonProperty("TDD_LWPRC") val lowPrice: String?,
        @JsonProperty("TDD_CLSPRC") val closePrice: String?,
        @JsonProperty("ACC_TRDVOL") val volume: String?,
        @JsonProperty("FLUC_RT") val changeRate: String?,
        @JsonProperty("MKTCAP") val marketCap: String?,
    ) {
        fun toStockDailyPrice(date: LocalDate): StockDailyPrice? {
            val close = closePrice?.replace(",", "")?.toLongOrNull() ?: return null
            if (close <= 0) return null
            return StockDailyPrice(
                stockCode = stockCode,
                date = date,
                openPrice = openPrice?.replace(",", "")?.toLongOrNull() ?: close,
                highPrice = highPrice?.replace(",", "")?.toLongOrNull() ?: close,
                lowPrice = lowPrice?.replace(",", "")?.toLongOrNull() ?: close,
                closePrice = close,
                volume = volume?.replace(",", "")?.toLongOrNull() ?: 0,
                changeRate = changeRate?.toBigDecimalOrNull(),
                marketCap = marketCap?.replace(",", "")?.toLongOrNull(),
            )
        }
    }
}
