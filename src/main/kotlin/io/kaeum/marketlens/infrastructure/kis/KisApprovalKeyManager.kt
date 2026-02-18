package io.kaeum.marketlens.infrastructure.kis

import com.fasterxml.jackson.annotation.JsonProperty
import io.kaeum.marketlens.global.exception.BusinessException
import io.kaeum.marketlens.global.exception.ErrorCode
import io.kaeum.marketlens.global.util.withRetry
import io.kaeum.marketlens.infrastructure.config.KisProperties
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
@Profile("!test")
class KisApprovalKeyManager(
    @Qualifier("kisWebClient") private val webClient: WebClient,
    private val kisProperties: KisProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mutex = Mutex()

    companion object {
        private const val ENDPOINT_APPROVAL = "/oauth2/Approval"
        private const val KEY_GRANT_TYPE = "grant_type"
        private const val KEY_APPKEY = "appkey"
        private const val KEY_SECRETKEY = "secretkey"
        private const val GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials"
    }

    @Volatile
    private var approvalKey: String? = null

    suspend fun getApprovalKey(): String {
        approvalKey?.let { return it }

        return mutex.withLock {
            approvalKey?.let { return@withLock it }
            fetchApprovalKey()
        }
    }

    fun invalidate() {
        approvalKey = null
        log.info("KIS approval key invalidated")
    }

    private suspend fun fetchApprovalKey(): String {
        log.info("Fetching KIS WebSocket approval key...")

        val response = withRetry(maxAttempts = 3, initialDelayMs = 2000) {
            try {
                webClient.post()
                    .uri(ENDPOINT_APPROVAL)
                    .bodyValue(
                        mapOf(
                            KEY_GRANT_TYPE to GRANT_TYPE_CLIENT_CREDENTIALS,
                            KEY_APPKEY to kisProperties.appKey,
                            KEY_SECRETKEY to kisProperties.appSecret,
                        )
                    )
                    .retrieve()
                    .awaitBody<KisApprovalKeyResponse>()
            } catch (e: Exception) {
                throw BusinessException(ErrorCode.KIS_APPROVAL_KEY_ERROR)
            }
        }

        val key = response.approvalKey
        approvalKey = key
        log.info("KIS WebSocket approval key obtained")
        return key
    }

    private data class KisApprovalKeyResponse(
        @JsonProperty("approval_key") val approvalKey: String,
    )
}
