package io.kaeum.marketlens.infrastructure.kis

import io.kaeum.marketlens.application.port.out.TickProducer
import io.kaeum.marketlens.domain.price.RealtimeTick
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class InMemoryTickProducer : TickProducer {

    private val log = LoggerFactory.getLogger(javaClass)

    private val _ticks = MutableSharedFlow<RealtimeTick>(
        extraBufferCapacity = 10_000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val ticks: SharedFlow<RealtimeTick> = _ticks.asSharedFlow()

    override suspend fun emit(tick: RealtimeTick) {
        val emitted = _ticks.tryEmit(tick)
        if (!emitted) {
            log.warn("Tick buffer overflow, dropped tick for {}", tick.stockCode)
        }
    }
}
