package com.team3.gudit.auth.jwt

import com.team3.gudit.user.domain.entity.Role
import com.team3.gudit.user.domain.entity.User
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Base64

class TokenProviderNullSafetyTest {
    @Test
    fun `저장되지 않은 사용자의 토큰 발급을 거부한다`() {
        val properties = JwtProperties().apply {
            issuer = "test"
            secretKey = Base64.getEncoder().encodeToString(ByteArray(64) { 1 })
            accessTokenValidity = Duration.ofMinutes(30)
            refreshTokenValidity = Duration.ofDays(14)
        }
        val tokenProvider = TokenProvider(properties)
        val transientUser = User(role = Role.USER)

        assertThatThrownBy {
            tokenProvider.generateToken(transientUser, Duration.ofMinutes(30), TokenType.ACCESS)
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("토큰을 발급할 사용자 ID가 없습니다.")
    }
}
