package io.kaeum.marketlens.infrastructure.kis

import com.fasterxml.jackson.annotation.JsonProperty
import io.kaeum.marketlens.global.exception.BusinessException
import io.kaeum.marketlens.global.exception.ErrorCode
import io.kaeum.marketlens.global.util.withRetry
import io.kaeum.marketlens.infrastructure.config.KisProperties
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
@Profile("!test")
class KisTokenManager(
    @Qualifier("kisWebClient") private val webClient: WebClient,
    private val kisProperties: KisProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mutex = Mutex()

    companion object {
        private const val ENDPOINT_TOKEN = "/oauth2/tokenP"
        private const val KEY_GRANT_TYPE = "grant_type"
        private const val KEY_APPKEY = "appkey"
        private const val KEY_APPSECRET = "appsecret"
        private const val GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials"
    }

    @Volatile
    private var currentToken: KisToken? = null

    suspend fun getAccessToken(): String {
        currentToken?.let { token ->
            if (!token.isExpiringSoon(kisProperties.tokenRefreshBeforeMinutes)) {
                return token.accessToken
            }
        }

        return mutex.withLock {
            // Double-check after acquiring lock
            currentToken?.let { token ->
                if (!token.isExpiringSoon(kisProperties.tokenRefreshBeforeMinutes)) {
                    return@withLock token.accessToken
                }
            }
            refreshToken().accessToken
        }
    }

    private suspend fun refreshToken(): KisToken {
        log.info("Refreshing KIS access token...")

        val response = withRetry(maxAttempts = 3, initialDelayMs = 2000) {
            webClient.post()
                .uri(ENDPOINT_TOKEN)
                .bodyValue(
                    mapOf(
                        KEY_GRANT_TYPE to GRANT_TYPE_CLIENT_CREDENTIALS,
                        KEY_APPKEY to kisProperties.appKey,
                        KEY_APPSECRET to kisProperties.appSecret,
                    )
                )
                .retrieve()
                .awaitBody<KisTokenResponse>()
        }

        val token = KisToken(
            accessToken = response.accessToken,
            expiresAt = Instant.now().plus(response.expiresIn, ChronoUnit.SECONDS),
        )
        currentToken = token
        log.info("KIS access token refreshed. Expires at: ${token.expiresAt}")
        return token
    }

    @Scheduled(fixedRate = 600_000) // 10 minutes
    fun scheduledTokenCheck() {
        val token = currentToken ?: return
        if (token.isExpiringSoon(kisProperties.tokenRefreshBeforeMinutes)) {
            log.info("Scheduled token refresh triggered.")
            runBlocking {
                try {
                    mutex.withLock { refreshToken() }
                } catch (e: Exception) {
                    log.error("Scheduled token refresh failed: ${e.message}", e)
                }
            }
        }
    }

    data class KisToken(
        val accessToken: String,
        val expiresAt: Instant,
    ) {
        fun isExpiringSoon(beforeMinutes: Long): Boolean =
            Instant.now().plus(beforeMinutes, ChronoUnit.MINUTES).isAfter(expiresAt)
    }

    private data class KisTokenResponse(
        @JsonProperty("access_token") val accessToken: String,
        @JsonProperty("token_type") val tokenType: String,
        @JsonProperty("expires_in") val expiresIn: Long,
    )
}
