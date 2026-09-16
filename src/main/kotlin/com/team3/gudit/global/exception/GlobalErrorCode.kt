package com.team3.gudit.global.exception

import org.springframework.http.HttpStatus

enum class GlobalErrorCode(
    private val status: HttpStatus,
    private val code: String,
    private val message: String,
) : ErrorCode {
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON_001", "입력값이 올바르지 않습니다."),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "COMMON_002", "요청 파라미터 형식이 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_003", "예상치 못한 서버 에러가 발생했습니다.");

    override fun getStatus(): HttpStatus = status
    override fun getCode(): String = code
    override fun getMessage(): String = message
}
