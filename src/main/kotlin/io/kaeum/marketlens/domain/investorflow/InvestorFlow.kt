package io.kaeum.marketlens.domain.investorflow

import java.time.LocalDate

data class InvestorFlow(
    val stockCode: String,
    val tradingDate: LocalDate,
    val investorType: String,
    val sellVolume: Long,
    val buyVolume: Long,
    val netVolume: Long,
    val sellAmount: Long,
    val buyAmount: Long,
    val netAmount: Long,
)
