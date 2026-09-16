package com.team3.gudit.auth.exception

import com.team3.gudit.global.exception.ErrorCode
import com.team3.gudit.global.exception.ErrorCodeProvider
import org.springframework.stereotype.Component

@Component
class AuthErrorCodeProvider : ErrorCodeProvider<AuthErrorCode> {
    override fun getDomain(): String = "AUTH"
    override fun getErrorCodes(): List<ErrorCode> = AuthErrorCode.entries.toList()
}
