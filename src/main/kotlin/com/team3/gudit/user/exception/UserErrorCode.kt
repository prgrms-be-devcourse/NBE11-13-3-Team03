package com.team3.gudit.user.exception

import com.team3.gudit.global.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class UserErrorCode(
    private val status: HttpStatus,
    private val code: String,
    private val message: String,
) : ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자를 찾을 수 없습니다.");

    override fun getStatus(): HttpStatus = status
    override fun getCode(): String = code
    override fun getMessage(): String = message
}
