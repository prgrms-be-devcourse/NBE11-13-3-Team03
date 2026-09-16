package com.team3.gudit.auth.oauth2

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component

@Component
class OAuth2FailureHandler : SimpleUrlAuthenticationFailureHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationFailure(request: HttpServletRequest, response: HttpServletResponse, exception: AuthenticationException) {
        log.warn("OAuth2 login failed: {}", exception.message)
        response.sendRedirect("/login-failure.html")
    }
}
