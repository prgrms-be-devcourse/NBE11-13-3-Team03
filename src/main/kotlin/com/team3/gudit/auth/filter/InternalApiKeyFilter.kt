package com.team3.gudit.auth.filter

import com.team3.gudit.auth.exception.AuthErrorCode
import com.team3.gudit.global.exception.ErrorResponse
import jakarta.annotation.PostConstruct
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper
import java.io.IOException

@Component
class InternalApiKeyFilter(
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    @field:Value("\${internal.api-key}")
    private var internalApiKey: String? = null

    @PostConstruct
    internal fun validateInternalApiKey() {
        if (internalApiKey.isNullOrBlank()) {
            throw IllegalStateException("internal.api-key must not be blank")
        }
    }

    public override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.servletPath.startsWith(INTERNAL_API_PREFIX)

    @Throws(ServletException::class, IOException::class)
    public override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestApiKey = request.getHeader(INTERNAL_API_KEY_HEADER)

        if (requestApiKey == null || internalApiKey != requestApiKey) {
            val errorCode = AuthErrorCode.INVALID_INTERNAL_API_KEY

            response.status = errorCode.getStatus().value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = "UTF-8"

            objectMapper.writeValue(
                response.writer,
                ErrorResponse.from(errorCode),
            )
            return
        }

        filterChain.doFilter(request, response)
    }

    private companion object {
        const val INTERNAL_API_PREFIX = "/api/internal/"
        const val INTERNAL_API_KEY_HEADER = "X-INTERNAL-KEY"
    }
}
