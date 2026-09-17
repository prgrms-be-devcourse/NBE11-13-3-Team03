package com.team3.gudit.auth.oauth2

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CustomOAuth2UserTest {
    @Test
    fun `사용자 이름 속성이 없으면 명확한 예외를 발생시킨다`() {
        val attributes = emptyMap<String, Any>()
        val userInfo = KakaoUserInfo(attributes)
        val oauth2User = CustomOAuth2User(
            user = null,
            provider = AuthProvider.KAKAO,
            userInfo = userInfo,
            attributes = attributes,
            nameAttributeKey = "id",
        )

        assertThatThrownBy { oauth2User.name }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("OAuth2 사용자 이름 속성이 없습니다.")
    }
}
