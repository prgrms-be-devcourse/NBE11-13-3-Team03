package com.team3.gudit.auth.oauth2

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CustomOAuth2UserTest {
    @Test
    fun `검증된 사용자 이름을 반환한다`() {
        val attributes = emptyMap<String, Any>()
        val userInfo = KakaoUserInfo(attributes)
        val oauth2User = CustomOAuth2User(
            user = null,
            provider = AuthProvider.KAKAO,
            userInfo = userInfo,
            attributes = attributes,
            principalName = "123",
        )

        assertEquals("123", oauth2User.name)
    }
}
