package com.team3.gudit.global.exception

class BusinessException private constructor(
    val errorCode: ErrorCode,
    message: String,
    cause: Throwable?,
) : RuntimeException(message, cause) {
    constructor(errorCode: ErrorCode) : this(errorCode, errorCode.getMessage(), null)
    constructor(errorCode: ErrorCode, logMessage: String) : this(errorCode, logMessage, null)
    constructor(errorCode: ErrorCode, cause: Throwable) : this(errorCode, errorCode.getMessage(), cause)
}
