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
@EnableConfigurationProperties(KisProperties::class, KrxProperties::class, NaverProperties::class, DartProperties::class)
class WebClientConfig(
    private val kisProperties: KisProperties,
    private val krxProperties: KrxProperties,
    private val naverProperties: NaverProperties,
    private val dartProperties: DartProperties,
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

    @Bean("naverWebClient")
    fun naverWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, naverProperties.connectionTimeoutMs.toInt())
            .responseTimeout(Duration.ofMillis(naverProperties.readTimeoutMs))
            .doOnConnected { conn ->
                conn.addHandlerLast(
                    ReadTimeoutHandler(naverProperties.readTimeoutMs, TimeUnit.MILLISECONDS)
                )
            }

        return WebClient.builder()
            .baseUrl(naverProperties.baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(5 * 1024 * 1024) }
            .build()
    }

    @Bean("dartWebClient")
    fun dartWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, dartProperties.connectionTimeoutMs.toInt())
            .responseTimeout(Duration.ofMillis(dartProperties.readTimeoutMs))
            .doOnConnected { conn ->
                conn.addHandlerLast(
                    ReadTimeoutHandler(dartProperties.readTimeoutMs, TimeUnit.MILLISECONDS)
                )
            }

        return WebClient.builder()
            .baseUrl(dartProperties.baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(5 * 1024 * 1024) }
            .build()
    }
}
