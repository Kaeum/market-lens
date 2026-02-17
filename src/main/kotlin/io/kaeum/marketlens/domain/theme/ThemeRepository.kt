package io.kaeum.marketlens.domain.theme

import org.springframework.data.repository.kotlin.CoroutineCrudRepository

interface ThemeRepository : CoroutineCrudRepository<Theme, Long> {

    suspend fun findAllByOrderByDisplayOrderAsc(): List<Theme>
}
