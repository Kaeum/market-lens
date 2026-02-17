package io.kaeum.marketlens.presentation.handler

import io.kaeum.marketlens.application.port.`in`.ThemeQueryUseCase
import io.kaeum.marketlens.global.common.ApiResponse
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait

@Component
class ThemeHandler(private val themeQueryUseCase: ThemeQueryUseCase) {

    companion object {
        private const val PARAM_SORT_BY = "sortBy"
        private const val PARAM_LIMIT = "limit"
        private const val PATH_VAR_THEME_ID = "themeId"
        private const val DEFAULT_PERFORMANCE_SORT = "avg_change_rate"
        private const val DEFAULT_LEADER_SORT = "change_rate"
        private const val DEFAULT_PERFORMANCE_LIMIT = 15
        private const val DEFAULT_LEADER_LIMIT = 10
    }

    suspend fun getAllThemes(request: ServerRequest): ServerResponse {
        val themes = themeQueryUseCase.getAllThemes()
        return ServerResponse.ok().bodyValueAndAwait(ApiResponse.ok(themes))
    }

    suspend fun getThemePerformance(request: ServerRequest): ServerResponse {
        val sortBy = request.queryParam(PARAM_SORT_BY).orElse(DEFAULT_PERFORMANCE_SORT)
        val limit = request.queryParam(PARAM_LIMIT).map { it.toInt() }.orElse(DEFAULT_PERFORMANCE_LIMIT)
        val performance = themeQueryUseCase.getThemePerformance(sortBy, limit)
        return ServerResponse.ok().bodyValueAndAwait(ApiResponse.ok(performance))
    }

    suspend fun getThemeLeaders(request: ServerRequest): ServerResponse {
        val themeId = request.pathVariable(PATH_VAR_THEME_ID).toLong()
        val sortBy = request.queryParam(PARAM_SORT_BY).orElse(DEFAULT_LEADER_SORT)
        val limit = request.queryParam(PARAM_LIMIT).map { it.toInt() }.orElse(DEFAULT_LEADER_LIMIT)
        val leaders = themeQueryUseCase.getThemeLeaders(themeId, sortBy, limit)
        return ServerResponse.ok().bodyValueAndAwait(ApiResponse.ok(leaders))
    }
}
