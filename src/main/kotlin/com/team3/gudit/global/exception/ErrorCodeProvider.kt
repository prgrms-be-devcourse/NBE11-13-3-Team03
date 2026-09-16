package com.team3.gudit.global.exception

interface ErrorCodeProvider<T> {
    fun getDomain(): String
    fun getErrorCodes(): List<ErrorCode>
}
