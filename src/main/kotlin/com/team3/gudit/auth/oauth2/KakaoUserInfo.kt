package com.team3.gudit.auth.oauth2

@JvmRecord
data class KakaoUserInfo(private val attributes: Map<String, Any>) : OAuth2UserInfo {
    override fun attributes(): Map<String, Any> = attributes
    override fun id(): Long? = attributes["id"]?.toString()?.toLongOrNull()

    override fun email(): String? {
        return kakaoAccount()?.get("email")?.toString()
    }

    override fun name(): String? {
        return profile()?.get("nickname")?.toString()
    }

    override fun imageUrl(): String? {
        return profile()?.get("profile_image_url")?.toString()
    }

    private fun kakaoAccount(): Map<*, *>? = attributes["kakao_account"] as? Map<*, *>

    private fun profile(): Map<*, *>? = kakaoAccount()?.get("profile") as? Map<*, *>
}
