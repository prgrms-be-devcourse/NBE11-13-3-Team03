package com.team3.gudit.auth.exception

import com.team3.gudit.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class AuthErrorCode(
    private val status: HttpStatus,
    private val code: String,
    private val message: String,
) : ErrorCode {
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH_001", "Refresh Token이 없습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_002", "유효하지 않은 Refresh Token입니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "만료된 Refresh Token입니다."),
    REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "AUTH_004", "저장된 Refresh Token과 일치하지 않습니다."),
    INVALID_TOKEN_TYPE(HttpStatus.UNAUTHORIZED, "AUTH_005", "올바른 Refresh Token이 아닙니다."),
    ACCESS_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH_006", "Access Token이 없습니다."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_007", "유효하지 않음 Access Token입니다.");

    override fun getStatus(): HttpStatus = status
    override fun getCode(): String = code
    override fun getMessage(): String = message
}
