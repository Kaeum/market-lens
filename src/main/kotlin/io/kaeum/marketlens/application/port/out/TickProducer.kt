package io.kaeum.marketlens.application.port.out

import io.kaeum.marketlens.domain.price.RealtimeTick

interface TickProducer {
    suspend fun emit(tick: RealtimeTick)
}
