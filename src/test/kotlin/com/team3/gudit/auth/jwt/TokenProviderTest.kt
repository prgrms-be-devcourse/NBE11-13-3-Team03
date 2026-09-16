package com.team3.gudit.auth.jwt

import com.team3.gudit.auth.security.CustomUserDetails
import com.team3.gudit.user.domain.entity.Role
import com.team3.gudit.user.domain.entity.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

@SpringBootTest
class TokenProviderTest @Autowired constructor(private val tokenProvider: TokenProvider) {
    private val user = User(id = 1L, role = Role.USER)
    @Test
    @DisplayName("정상적인 Access Token을 검증하면 VALID를 반환한다")
    fun validateAccessToken_success() {

        // given
        val token = tokenProvider.generateToken(
                user,
                Duration.ofMinutes(30),
                TokenType.ACCESS
        )

        // when
        val status = tokenProvider.validateToken(
                token,
                TokenType.ACCESS
        )

        // then
        assertThat(status)
                .isEqualTo(TokenStatus.VALID)
    }

    @Test
    @DisplayName("정상적인 Refresh Token은 VALID를 반환한다")
    fun validateRefreshToken_success() {

        // given
        val refreshToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofDays(14),
                        TokenType.REFRESH
                )

        // when
        val status =
                tokenProvider.validateToken(
                        refreshToken,
                        TokenType.REFRESH
                )

        // then
        assertThat(status)
                .isEqualTo(TokenStatus.VALID)
    }

    @Test
    @DisplayName("Access Token을 Refresh Token으로 검증하면 INVALID를 반환한다")
    fun validateAccessToken_withRefreshType_fail() {

        // given
        val token = tokenProvider.generateToken(
                user,
                Duration.ofMinutes(30),
                TokenType.ACCESS
        )

        // when
        val status = tokenProvider.validateToken(
                token,
                TokenType.REFRESH
        )

        // then
        assertThat(status)
                .isEqualTo(TokenStatus.INVALID)
    }

    @Test
    @DisplayName("Refresh Token을 Access Token으로 검증하면 INVALID를 반환한다")
    fun validateRefreshToken_asAccessToken_fail() {

        // given
        val refreshToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofDays(14),
                        TokenType.REFRESH
                )

        // when
        val status =
                tokenProvider.validateToken(
                        refreshToken,
                        TokenType.ACCESS
                )

        // then
        assertThat(status)
                .isEqualTo(TokenStatus.INVALID)
    }

    @Test
    @DisplayName("만료된 Token은 EXPIRED를 반환한다")
    fun validateExpiredToken_fail() {

        // given
        val expiredToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofSeconds(-1),
                        TokenType.ACCESS
                )

        // when
        val status =
                tokenProvider.validateToken(
                        expiredToken,
                        TokenType.ACCESS
                )

        // then
        assertThat(status)
                .isEqualTo(TokenStatus.EXPIRED)
    }

    @Test
    @DisplayName("변조된 Token은 INVALID를 반환한다")
    fun validateTamperedToken_fail() {

        // given
        val accessToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofMinutes(30),
                        TokenType.ACCESS
                )

        val tamperedToken =
                accessToken.substring(
                        0,
                        accessToken.length - 1
                ) + "invalid"

        // when
        val status =
                tokenProvider.validateToken(
                        tamperedToken,
                        TokenType.ACCESS
                )

        // then
        assertThat(status)
                .isEqualTo(TokenStatus.INVALID)
    }

    @Test
    @DisplayName("Token에서 사용자 ID를 추출할 수 있다")
    fun getUserId_success() {

        // given
        val accessToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofMinutes(30),
                        TokenType.ACCESS
                )

        // when
        val userId =
                tokenProvider.getUserId(accessToken)

        // then
        assertThat(userId)
                .isEqualTo(1L)
    }

    @Test
    @DisplayName("Token에서 사용자 권한을 추출할 수 있다")
    fun getRole_success() {

        // given
        val accessToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofMinutes(30),
                        TokenType.ACCESS
                )

        // when
        val role =
                tokenProvider.getRole(accessToken)

        // then
        assertThat(role)
                .isEqualTo(Role.USER)
    }

    @Test
    @DisplayName("Access Token으로 Authentication 객체를 생성할 수 있다")
    fun getAuthentication_success() {

        // given
        val accessToken =
                tokenProvider.generateToken(
                        user,
                        Duration.ofMinutes(30),
                        TokenType.ACCESS
                )

        // when
        val authentication =
                tokenProvider.getAuthentication(
                        accessToken
                )

        // then
        assertThat(authentication.isAuthenticated)
                .isTrue()

        assertThat(authentication.principal)
                .isInstanceOf(CustomUserDetails::class.java)

        val principal = authentication.principal as CustomUserDetails

        assertThat(principal.userId)
                .isEqualTo(1L)
    }
}
