package io.kaeum.marketlens

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MarketLensApplication

fun main(args: Array<String>) {
    runApplication<MarketLensApplication>(*args)
}
