package io.kaeum.marketlens.infrastructure.news

import io.kaeum.marketlens.global.exception.BusinessException
import io.kaeum.marketlens.global.exception.ErrorCode
import io.kaeum.marketlens.infrastructure.config.DartProperties
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Component
@Profile("!test")
class DartApiClient(
    @Qualifier("dartWebClient") private val webClient: WebClient,
    private val dartProperties: DartProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun fetchTodayDisclosures(beginDate: String, endDate: String): List<DartDisclosure> {
        var lastException: Exception? = null

        repeat(MAX_RETRY_ATTEMPTS) { attempt ->
            try {
                val response = webClient.get()
                    .uri { uriBuilder ->
                        uriBuilder.path("/list.json")
                            .queryParam("crtfc_key", dartProperties.apiKey)
                            .queryParam("bgn_de", beginDate)
                            .queryParam("end_de", endDate)
                            .queryParam("page_count", 100)
                            .build()
                    }
                    .retrieve()
                    .bodyToMono<DartResponse>()
                    .awaitSingleOrNull()

                if (response == null) return emptyList()

                if (response.status != "000") {
                    if (response.status == "013") {
                        // 013 = "조회된 데이터가 없습니다" — not an error
                        return emptyList()
                    }
                    log.warn("DART API returned status={}, message={}", response.status, response.message)
                    return emptyList()
                }

                return response.list.map { it.toDomain() }
            } catch (e: Exception) {
                lastException = e
                log.warn("DART disclosure fetch failed, attempt {}/{}: {}", attempt + 1, MAX_RETRY_ATTEMPTS, e.message)
            }
        }

        log.error("DART API failed after {} attempts: {}", MAX_RETRY_ATTEMPTS, lastException?.message)
        throw BusinessException(ErrorCode.DART_API_ERROR)
    }

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val DART_VIEWER_BASE_URL = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo="

        val MAJOR_REPORT_TYPES = setOf(
            "주요사항보고서",
            "사업보고서",
            "분기보고서",
            "반기보고서",
            "감사보고서",
            "합병등종료보고서",
            "주요경영사항",
        )

        fun buildViewerUrl(rceptNo: String): String = "$DART_VIEWER_BASE_URL$rceptNo"
    }
}

data class DartResponse(
    val status: String = "",
    val message: String = "",
    val page_no: Int = 1,
    val page_count: Int = 10,
    val total_count: Int = 0,
    val total_page: Int = 0,
    val list: List<DartDisclosureDto> = emptyList(),
)

data class DartDisclosureDto(
    val corp_name: String = "",
    val corp_code: String = "",
    val stock_code: String? = null,
    val report_nm: String = "",
    val rcept_no: String = "",
    val flr_nm: String = "",
    val rcept_dt: String = "",
    val rm: String = "",
) {
    fun toDomain(): DartDisclosure = DartDisclosure(
        corpName = corp_name,
        corpCode = corp_code,
        stockCode = stock_code,
        reportName = report_nm,
        receiptNo = rcept_no,
        filerName = flr_nm,
        receiptDate = rcept_dt,
        remark = rm,
        sourceUrl = DartApiClient.buildViewerUrl(rcept_no),
    )
}

data class DartDisclosure(
    val corpName: String,
    val corpCode: String,
    val stockCode: String?,
    val reportName: String,
    val receiptNo: String,
    val filerName: String,
    val receiptDate: String,
    val remark: String,
    val sourceUrl: String,
)
