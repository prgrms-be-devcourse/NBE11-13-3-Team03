package com.team3.gudit.auth.oauth2

import com.team3.gudit.auth.jwt.JwtProperties
import com.team3.gudit.auth.service.TokenService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2SuccessHandler(
    private val tokenService: TokenService,
    private val jwtProperties: JwtProperties,
) : SimpleUrlAuthenticationSuccessHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun onAuthenticationSuccess(request: HttpServletRequest, response: HttpServletResponse, authentication: Authentication) {
        log.info("OAuth2 로그인 성공 핸들러 실행")
        val principal = authentication.principal as CustomOAuth2User
        val tokens = tokenService.issueToken(requireNotNull(principal.user))
        addCookie(response, ACCESS_TOKEN_COOKIE, tokens.accessToken, jwtProperties.accessTokenValidity.seconds.toInt())
        addCookie(response, REFRESH_TOKEN_COOKIE, tokens.refreshToken, jwtProperties.refreshTokenValidity.seconds.toInt())
        log.info("JWT 쿠키 저장 완료")
        if (response.isCommitted) {
            log.warn("Response already committed")
            return
        }
        redirectStrategy.sendRedirect(request, response, "/login-success.html")
    }

    private fun addCookie(response: HttpServletResponse, name: String, value: String, maxAge: Int) {
        val cookie = Cookie(name, value)
        cookie.isHttpOnly = true
        cookie.secure = false
        cookie.path = "/"
        cookie.maxAge = maxAge
        response.addCookie(cookie)
    }

    companion object {
        private const val ACCESS_TOKEN_COOKIE = "access_token"
        private const val REFRESH_TOKEN_COOKIE = "refresh_token"
    }
}
