package io.kaeum.marketlens.infrastructure.config

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

@Configuration
@Profile("!test")
@EnableConfigurationProperties(KisProperties::class, KrxProperties::class)
class WebClientConfig(
    private val kisProperties: KisProperties,
    private val krxProperties: KrxProperties,
) {

    @Bean("kisWebClient")
    fun kisWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, kisProperties.connectionTimeoutMs.toInt())
            .responseTimeout(Duration.ofMillis(kisProperties.readTimeoutMs))
            .doOnConnected { conn ->
                conn.addHandlerLast(
                    ReadTimeoutHandler(kisProperties.readTimeoutMs, TimeUnit.MILLISECONDS)
                )
            }

        return WebClient.builder()
            .baseUrl(kisProperties.baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(1 * 1024 * 1024) }
            .build()
    }

    @Bean("krxWebClient")
    fun krxWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, krxProperties.connectionTimeoutMs.toInt())
            .responseTimeout(Duration.ofMillis(krxProperties.readTimeoutMs))
            .doOnConnected { conn ->
                conn.addHandlerLast(
                    ReadTimeoutHandler(krxProperties.readTimeoutMs, TimeUnit.MILLISECONDS)
                )
            }

        return WebClient.builder()
            .baseUrl(krxProperties.baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }
            .build()
    }
}
