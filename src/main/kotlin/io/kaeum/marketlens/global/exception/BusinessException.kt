package io.kaeum.marketlens.global.exception

class BusinessException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)
