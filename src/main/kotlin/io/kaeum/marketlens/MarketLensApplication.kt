package io.kaeum.marketlens

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class MarketLensApplication

fun main(args: Array<String>) {
    runApplication<MarketLensApplication>(*args)
}
