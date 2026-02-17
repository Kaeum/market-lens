package io.kaeum.marketlens.presentation.handler

import io.kaeum.marketlens.application.port.`in`.ThemeQueryUseCase
import io.kaeum.marketlens.global.common.ApiResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class ThemeHandler(private val themeQueryUseCase: ThemeQueryUseCase) {

    suspend fun getAllThemes(request: ServerRequest): ServerResponse {
        val themes = themeQueryUseCase.getAllThemes()
        return ServerResponse.ok().bodyValueAndAwait(ApiResponse.ok(themes))
    }

    suspend fun getThemePerformance(request: ServerRequest): ServerResponse {
        val sortBy = request.queryParam("sortBy").orElse("avg_change_rate")
        val limit = request.queryParam("limit").map { it.toInt() }.orElse(15)
        val performance = themeQueryUseCase.getThemePerformance(sortBy, limit)
        return ServerResponse.ok().bodyValueAndAwait(ApiResponse.ok(performance))
    }

    suspend fun getThemeLeaders(request: ServerRequest): ServerResponse {
        val themeId = request.pathVariable("themeId").toLong()
        val sortBy = request.queryParam("sortBy").orElse("change_rate")
        val limit = request.queryParam("limit").map { it.toInt() }.orElse(10)
        val leaders = themeQueryUseCase.getThemeLeaders(themeId, sortBy, limit)
        return ServerResponse.ok().bodyValueAndAwait(ApiResponse.ok(leaders))
    }
}
