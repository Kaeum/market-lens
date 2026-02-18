package io.kaeum.marketlens.global.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    // Common
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 서버 오류가 발생했습니다"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다"),

    // Theme
    THEME_NOT_FOUND(HttpStatus.NOT_FOUND, "테마를 찾을 수 없습니다"),

    // Stock
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "종목을 찾을 수 없습니다"),

    // External API
    EXTERNAL_API_ERROR(HttpStatus.BAD_GATEWAY, "외부 API 호출 중 오류가 발생했습니다"),
    KIS_TOKEN_ERROR(HttpStatus.UNAUTHORIZED, "KIS 토큰 발급/갱신에 실패했습니다"),
    KIS_API_ERROR(HttpStatus.BAD_GATEWAY, "KIS API 호출 중 오류가 발생했습니다"),
    KIS_WEBSOCKET_CONNECTION_ERROR(HttpStatus.BAD_GATEWAY, "KIS WebSocket 연결에 실패했습니다"),
    KIS_WEBSOCKET_SUBSCRIPTION_ERROR(HttpStatus.BAD_GATEWAY, "KIS WebSocket 구독에 실패했습니다"),
    KIS_APPROVAL_KEY_ERROR(HttpStatus.UNAUTHORIZED, "KIS WebSocket 인증키 발급에 실패했습니다"),
    KRX_API_ERROR(HttpStatus.BAD_GATEWAY, "KRX API 호출 중 오류가 발생했습니다"),
}
