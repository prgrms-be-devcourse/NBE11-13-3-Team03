package com.team3.gudit.user.exception

import com.team3.gudit.global.exception.ErrorCode
import com.team3.gudit.global.exception.ErrorCodeProvider
import org.springframework.stereotype.Component

@Component
class UserErrorCodeProvider : ErrorCodeProvider<UserErrorCode> {
    override fun getDomain(): String = "USER"

    override fun getErrorCodes(): List<ErrorCode> = UserErrorCode.entries.toList()
}
