package com.team3.gudit.auth.oauth2

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class KakaoUserInfoTest {
    @Test
    fun `프로필 속성이 없으면 null을 반환한다`() {
        val userInfo = KakaoUserInfo(
            mapOf(
                "id" to 123L,
                "kakao_account" to mapOf(
                    "email" to "user@example.com",
                    "profile" to emptyMap<String, Any>(),
                ),
            ),
        )

        assertThat(userInfo.name()).isNull()
        assertThat(userInfo.imageUrl()).isNull()
    }

    @Test
    fun `카카오 계정 정보가 없으면 선택 속성은 null을 반환한다`() {
        val userInfo = KakaoUserInfo(mapOf("id" to 123L))

        assertThat(userInfo.email()).isNull()
        assertThat(userInfo.name()).isNull()
        assertThat(userInfo.imageUrl()).isNull()
    }

    @Test
    fun `숫자가 아닌 사용자 식별자는 null을 반환한다`() {
        val userInfo = KakaoUserInfo(mapOf("id" to "invalid"))

        assertThat(userInfo.id()).isNull()
    }
}
