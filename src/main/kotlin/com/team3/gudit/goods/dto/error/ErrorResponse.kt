package com.team3.gudit.goods.dto.error

import java.time.LocalDateTime

@JvmRecord
data class ErrorResponse(
    val status: Int,
    val code: String?,
    val message: String?,
    val api: String?,
    val location: String?,
    val time: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun of(
            status: Int,
            code: String?,
            message: String?,
            api: String?,
            location: String?,
            time: LocalDateTime?,
        ): ErrorResponse =
            ErrorResponse(
                status,
                code,
                message,
                api,
                location,
                time,
            )

        @JvmStatic
        fun builder(): ErrorResponseBuilder = ErrorResponseBuilder()
    }

    class ErrorResponseBuilder {
        private var status: Int = 0
        private var code: String? = null
        private var message: String? = null
        private var api: String? = null
        private var location: String? = null
        private var time: LocalDateTime? = null

        fun status(status: Int): ErrorResponseBuilder = apply { this.status = status }

        fun code(code: String?): ErrorResponseBuilder = apply { this.code = code }

        fun message(message: String?): ErrorResponseBuilder = apply { this.message = message }

        fun api(api: String?): ErrorResponseBuilder = apply { this.api = api }

        fun location(location: String?): ErrorResponseBuilder = apply { this.location = location }

        fun time(time: LocalDateTime?): ErrorResponseBuilder = apply { this.time = time }

        fun build(): ErrorResponse =
            ErrorResponse(
                status,
                code,
                message,
                api,
                location,
                time,
            )
    }
}
