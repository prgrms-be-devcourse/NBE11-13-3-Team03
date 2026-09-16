package com.team3.gudit.auth.filter

import com.team3.gudit.auth.exception.AuthErrorCode
import com.team3.gudit.auth.jwt.TokenProvider
import com.team3.gudit.auth.jwt.TokenStatus
import com.team3.gudit.auth.jwt.TokenType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class TokenAuthenticationFilter(private val tokenProvider: TokenProvider) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith("/api/internal/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val token = resolveToken(request)
        if (token == null) {
            request.setAttribute("auth_error", AuthErrorCode.ACCESS_TOKEN_NOT_FOUND)
            filterChain.doFilter(request, response)
            return
        }
        val status = tokenProvider.validateToken(token, TokenType.ACCESS)
        if (status == TokenStatus.EXPIRED || status == TokenStatus.INVALID) {
            request.setAttribute("auth_error", AuthErrorCode.INVALID_ACCESS_TOKEN)
            filterChain.doFilter(request, response)
            return
        }
        SecurityContextHolder.getContext().authentication = tokenProvider.getAuthentication(token)
        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == "access_token" }?.value
}
