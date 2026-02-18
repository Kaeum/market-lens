package io.kaeum.marketlens.infrastructure.kis

import io.kaeum.marketlens.application.port.out.TickProducer
import io.kaeum.marketlens.infrastructure.config.KisProperties
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.core.Disposable
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

@Component
@Profile("!test")
class KisWebSocketClient(
    private val kisProperties: KisProperties,
    private val approvalKeyManager: KisApprovalKeyManager,
    private val tickProducer: TickProducer,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()

    private val subscribedStocks = ConcurrentHashMap.newKeySet<String>()
    private val outboundSink = Sinks.many().unicast().onBackpressureBuffer<String>()

    @Volatile
    private var state = ConnectionState.DISCONNECTED

    @Volatile
    private var connection: Disposable? = null

    @Volatile
    private var currentBackoffMs = kisProperties.wsReconnectInitialDelayMs

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING
    }

    fun getState(): ConnectionState = state

    fun getSubscribedStocks(): Set<String> = subscribedStocks.toSet()

    suspend fun connect() {
        mutex.withLock {
            if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING) {
                log.info("WebSocket already {} — skipping connect", state)
                return
            }
            state = ConnectionState.CONNECTING
        }
        doConnect()
    }

    suspend fun disconnect() {
        mutex.withLock {
            state = ConnectionState.DISCONNECTED
            connection?.dispose()
            connection = null
            subscribedStocks.clear()
            log.info("KIS WebSocket disconnected")
        }
    }

    suspend fun subscribe(stockCode: String): Boolean {
        if (subscribedStocks.size >= kisProperties.wsMaxSubscriptions) {
            log.warn(
                "Max subscriptions ({}) reached, cannot subscribe {}",
                kisProperties.wsMaxSubscriptions, stockCode
            )
            return false
        }
        if (!subscribedStocks.add(stockCode)) {
            log.debug("Already subscribed to {}", stockCode)
            return true
        }

        sendSubscriptionMessage(stockCode, subscribe = true)
        log.info("Subscribed to {} (total: {})", stockCode, subscribedStocks.size)
        return true
    }

    suspend fun unsubscribe(stockCode: String) {
        if (!subscribedStocks.remove(stockCode)) {
            return
        }
        sendSubscriptionMessage(stockCode, subscribe = false)
        log.info("Unsubscribed from {} (total: {})", stockCode, subscribedStocks.size)
    }

    private suspend fun doConnect() {
        try {
            val approvalKey = approvalKeyManager.getApprovalKey()
            val wsUri = URI.create(kisProperties.wsUrl + "/tryitout/$TR_ID_TRADE")
            val client = ReactorNettyWebSocketClient()

            log.info("Connecting to KIS WebSocket: {}", wsUri)

            val disposable = client.execute(wsUri) { session ->
                val outbound = session.send(
                    outboundSink.asFlux().map { session.textMessage(it) }
                )

                val inbound = session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext { raw -> handleMessage(raw, approvalKey) }
                    .doOnError { e -> log.error("WebSocket receive error: {}", e.message) }
                    .then()

                Mono.zip(outbound, inbound).then()
            }.doOnTerminate {
                handleDisconnect()
            }.subscribe(
                { log.info("KIS WebSocket session completed") },
                { e -> log.error("KIS WebSocket connection error: {}", e.message) }
            )

            connection = disposable
            state = ConnectionState.CONNECTED
            currentBackoffMs = kisProperties.wsReconnectInitialDelayMs
            log.info("KIS WebSocket connected")
        } catch (e: Exception) {
            log.error("Failed to connect KIS WebSocket: {}", e.message)
            state = ConnectionState.DISCONNECTED
            scheduleReconnect()
        }
    }

    private fun handleMessage(rawMessage: String, approvalKey: String) {
        when (val message = KisWebSocketMessageParser.parse(rawMessage)) {
            is KisWebSocketMessage.Trade -> {
                scope.launch {
                    tickProducer.emit(message.tick)
                }
            }
            is KisWebSocketMessage.Heartbeat -> {
                // Echo back heartbeat (PINGPONG)
                outboundSink.tryEmitNext(rawMessage)
                log.debug("Heartbeat echoed")
            }
            is KisWebSocketMessage.SubscriptionResponse -> {
                if (message.success) {
                    log.info("Subscription confirmed: trId={}, stockCode={}", message.trId, message.stockCode)
                } else {
                    log.warn("Subscription failed: trId={}, stockCode={}", message.trId, message.stockCode)
                    subscribedStocks.remove(message.stockCode)
                }
            }
            is KisWebSocketMessage.Unknown -> {
                log.debug("Unknown message: {}", rawMessage.take(100))
            }
        }
    }

    private fun handleDisconnect() {
        if (state == ConnectionState.DISCONNECTED) {
            return
        }
        log.warn("KIS WebSocket disconnected unexpectedly")
        state = ConnectionState.RECONNECTING
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        scope.launch {
            if (state == ConnectionState.DISCONNECTED) return@launch

            val delayMs = currentBackoffMs
            currentBackoffMs = min(currentBackoffMs * 2, kisProperties.wsReconnectMaxDelayMs)

            log.info("Reconnecting KIS WebSocket in {}ms...", delayMs)
            delay(delayMs)

            if (!isActive || state == ConnectionState.DISCONNECTED) return@launch

            approvalKeyManager.invalidate()
            doConnect()

            // Re-subscribe after reconnection
            if (state == ConnectionState.CONNECTED) {
                resubscribeAll()
            }
        }
    }

    private suspend fun resubscribeAll() {
        val stocks = subscribedStocks.toSet()
        log.info("Re-subscribing {} stocks after reconnect", stocks.size)
        stocks.forEach { stockCode ->
            sendSubscriptionMessage(stockCode, subscribe = true)
            delay(100) // Rate limit between subscriptions
        }
    }

    private suspend fun sendSubscriptionMessage(stockCode: String, subscribe: Boolean) {
        val approvalKey = approvalKeyManager.getApprovalKey()
        val trType = if (subscribe) "1" else "2"

        val message = """
            {
              "header": {
                "approval_key": "$approvalKey",
                "custtype": "P",
                "tr_type": "$trType",
                "content-type": "utf-8"
              },
              "body": {
                "input": {
                  "tr_id": "$TR_ID_TRADE",
                  "tr_key": "$stockCode"
                }
              }
            }
        """.trimIndent()

        outboundSink.tryEmitNext(message)
    }

    @PreDestroy
    fun destroy() {
        connection?.dispose()
        scope.cancel()
        log.info("KIS WebSocket client destroyed")
    }

    companion object {
        const val TR_ID_TRADE = "H0STCNT0"
    }
}
