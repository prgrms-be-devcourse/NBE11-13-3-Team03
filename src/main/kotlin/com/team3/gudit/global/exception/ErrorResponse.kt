package com.team3.gudit.global.exception

@JvmRecord
data class ErrorResponse(
    val code: String,
    val message: String,
    val fieldErrors: Map<String, String>,
) {
    companion object {
        @JvmStatic
        fun from(errorCode: ErrorCode): ErrorResponse = ErrorResponse(
            errorCode.getCode(),
            errorCode.getMessage(),
            emptyMap(),
        )

        @JvmStatic
        fun validation(code: String, message: String, fieldErrors: Map<String, String>): ErrorResponse =
            ErrorResponse(code, message, fieldErrors)

        @JvmStatic
        fun internalServerError(): ErrorResponse = ErrorResponse(
            "COMMON_500",
            "서버 내부 오류가 발생했습니다.",
            emptyMap(),
        )
    }
}
