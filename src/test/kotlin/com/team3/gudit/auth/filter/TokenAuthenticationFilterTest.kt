package com.team3.gudit.auth.filter

import com.team3.gudit.auth.exception.AuthErrorCode
import com.team3.gudit.auth.jwt.TokenProvider
import com.team3.gudit.auth.jwt.TokenStatus
import com.team3.gudit.auth.jwt.TokenType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class TokenAuthenticationFilterTest {
    @Test
    fun internalApiSkipsJwtValidation() = assertInternalRequestSkipsJwt("")

    @Test
    fun internalApiWithContextPathSkipsJwtValidation() = assertInternalRequestSkipsJwt("/gudit")

    private fun assertInternalRequestSkipsJwt(context: String) {
        val provider = mock(TokenProvider::class.java)
        val filter = TokenAuthenticationFilter(provider)
        val path = "/api/internal/payments/GUDIT_test/cs-status"
        val request = MockHttpServletRequest("GET", context + path).apply {
            contextPath = context
            servletPath = path
            setCookies(Cookie("access_token", "invalid-token"))
        }
        val chain = mock(FilterChain::class.java)
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, chain)
        verifyNoInteractions(provider)
        verify(chain).doFilter(request, response)
        assertThat(request.getAttribute("auth_error")).isNull()
    }

    @Test
    fun similarButNonInternalPathStillValidatesJwt() {
        val provider = mock(TokenProvider::class.java)
        `when`(provider.validateToken("invalid-token", TokenType.ACCESS)).thenReturn(TokenStatus.INVALID)
        val request = MockHttpServletRequest("GET", "/api/internalized/orders").apply {
            servletPath = "/api/internalized/orders"
            setCookies(Cookie("access_token", "invalid-token"))
        }
        TokenAuthenticationFilter(provider).doFilter(request, MockHttpServletResponse(), mock(FilterChain::class.java))
        verify(provider).validateToken("invalid-token", TokenType.ACCESS)
        assertThat(request.getAttribute("auth_error")).isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN)
    }
}
