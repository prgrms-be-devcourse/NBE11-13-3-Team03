package com.team3.gudit.auth.controller

import com.team3.gudit.auth.filter.TokenAuthenticationFilter
import com.team3.gudit.auth.jwt.JwtProperties
import com.team3.gudit.auth.security.CustomUserDetails
import com.team3.gudit.auth.service.AuthService
import com.team3.gudit.auth.service.TokenService
import com.team3.gudit.user.domain.entity.Role
import jakarta.servlet.http.Cookie
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

@WebMvcTest(
    controllers = [AuthController::class],
    excludeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [TokenAuthenticationFilter::class])],
)
@AutoConfigureMockMvc(addFilters = false)
@MockitoBean(types = [TokenService::class, AuthService::class, JwtProperties::class])
class AuthControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val tokenService: TokenService,
    private val authService: AuthService,
    private val jwtProperties: JwtProperties,
) {
    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    @DisplayName("Refresh Token 재발급에 성공하면 새로운 Access Token과 Refresh Token을 쿠키로 반환한다")
    fun reissue_success() {
        // given
        val oldRefreshToken = "old-refresh-token"
        val tokens = TokenService.TokenPair("new-access-token", "new-refresh-token")
        `when`(tokenService.reissueToken(oldRefreshToken)).thenReturn(tokens)
        `when`(jwtProperties.accessTokenValidity).thenReturn(Duration.ofMinutes(30))
        `when`(jwtProperties.refreshTokenValidity).thenReturn(Duration.ofDays(14))

        // when
        val result = mockMvc.perform(post("/api/auth/reissue").cookie(Cookie("refresh_token", oldRefreshToken)))
            .andExpect(status().isOk).andReturn()

        // then
        verify(tokenService).reissueToken(oldRefreshToken)
        val cookies = result.response.getHeaders(HttpHeaders.SET_COOKIE)
        assertThat(cookies).hasSize(2)
        assertThat(cookies).anySatisfy { cookie ->
            assertThat(cookie).contains("access_token=new-access-token")
            assertThat(cookie).contains("HttpOnly")
            assertThat(cookie).contains("Secure")
            assertThat(cookie).contains("SameSite=Lax")
            assertThat(cookie).contains("Path=/")
            assertThat(cookie).contains("Max-Age=1800")
        }
        assertThat(cookies).anySatisfy { cookie ->
            assertThat(cookie).contains("refresh_token=new-refresh-token")
            assertThat(cookie).contains("HttpOnly")
            assertThat(cookie).contains("Secure")
            assertThat(cookie).contains("SameSite=Lax")
            assertThat(cookie).contains("Path=/")
            assertThat(cookie).contains("Max-Age=1209600")
        }
    }

    @Test
    @DisplayName("Refresh Token 쿠키가 없으면 TokenService에 null을 전달한다")
    fun reissue_withoutRefreshToken() {
        `when`(tokenService.reissueToken(null)).thenThrow(IllegalArgumentException("Refresh Token이 없습니다."))
        mockMvc.perform(post("/api/auth/reissue")).andExpect(status().is5xxServerError)
        verify(tokenService).reissueToken(null)
    }

    @Test
    @DisplayName("로그아웃에 성공하면 Refresh Token을 삭제하고 Access Token과 Refresh Token 쿠키를 제거한다")
    fun logout_success() {
        // given
        val userId = 1L
        val principal = CustomUserDetails(userId, Role.USER)
        val authentication = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        SecurityContextHolder.getContext().authentication = authentication

        // when
        val result = mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent).andReturn()

        // then
        verify(authService).logout(userId)
        val cookies = result.response.cookies
        assertThat(cookies).hasSize(2)
        val accessCookie = cookies.first { it.name == "access_token" }
        assertThat(accessCookie.value).isEmpty()
        assertThat(accessCookie.maxAge).isZero()
        assertThat(accessCookie.path).isEqualTo("/")
        assertThat(accessCookie.isHttpOnly).isTrue()
        val refreshCookie = cookies.first { it.name == "refresh_token" }
        assertThat(refreshCookie.value).isEmpty()
        assertThat(refreshCookie.maxAge).isZero()
        assertThat(refreshCookie.path).isEqualTo("/")
        assertThat(refreshCookie.isHttpOnly).isTrue()
    }
}
