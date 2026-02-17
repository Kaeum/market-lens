package io.kaeum.marketlens.domain.stock

import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface StockProfileRepository : CoroutineCrudRepository<StockProfile, String>
