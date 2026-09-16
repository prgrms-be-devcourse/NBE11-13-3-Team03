package com.team3.gudit.auth.security

import com.team3.gudit.auth.exception.AuthErrorCode
import com.team3.gudit.global.exception.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class CustomAuthenticationEntryPoint(private val objectMapper: ObjectMapper) : AuthenticationEntryPoint {
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        val errorCode = (request.getAttribute("auth_error") as AuthErrorCode?)
            ?: AuthErrorCode.ACCESS_TOKEN_NOT_FOUND
        response.status = errorCode.getStatus().value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        objectMapper.writeValue(response.writer, ErrorResponse.from(errorCode))
    }
}
