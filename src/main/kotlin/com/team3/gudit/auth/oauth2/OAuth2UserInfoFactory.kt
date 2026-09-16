package com.team3.gudit.auth.oauth2

object OAuth2UserInfoFactory {
    @JvmStatic
    fun of(provider: AuthProvider, attributes: Map<String, Any>): OAuth2UserInfo = when (provider) {
        AuthProvider.KAKAO -> KakaoUserInfo(attributes)
    }
}
