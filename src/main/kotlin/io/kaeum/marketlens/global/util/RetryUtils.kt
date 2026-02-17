package io.kaeum.marketlens.global.util

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("io.kaeum.marketlens.global.util.RetryUtils")

suspend fun <T>
        withRetry(
    maxAttempts: Int = 3,
    initialDelayMs: Long = 1000,
    factor: Double = 2.0,
    retryOn: (Throwable) -> Boolean = { true },
    block: suspend () -> T,
): T {
    var lastException: Throwable? = null
    var currentDelay = initialDelayMs

    repeat(maxAttempts) { attempt ->
        try {
            return block()
        } catch (e: Throwable) {
            lastException = e
            if (attempt == maxAttempts - 1 || !retryOn(e)) {
                throw e
            }
            log.warn("Attempt ${attempt + 1}/$maxAttempts failed: ${e.message}. Retrying in ${currentDelay}ms...")
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong()
        }
    }

    throw lastException!!
}
