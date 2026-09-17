package com.team3.gudit.auth.oauth2

@JvmRecord
data class KakaoUserInfo(private val attributes: Map<String, Any>) : OAuth2UserInfo {
    override fun attributes(): Map<String, Any> = attributes
    override fun id(): Long? = attributes["id"]?.toString()?.toLong()

    override fun email(): String? {
        val account = kakaoAccount()
        return account?.get("email")?.toString()
    }

    override fun name(): String? {
        val profile = profile()
        return if (profile == null) null else profile["nickname"].toString()
    }

    override fun imageUrl(): String? {
        val profile = profile()
        return if (profile == null) null else profile["profile_image_url"].toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun kakaoAccount(): Map<String, Any>? = attributes["kakao_account"] as Map<String, Any>?

    @Suppress("UNCHECKED_CAST")
    private fun profile(): Map<String, Any>? = requireNotNull(kakaoAccount())["profile"] as Map<String, Any>?
}
