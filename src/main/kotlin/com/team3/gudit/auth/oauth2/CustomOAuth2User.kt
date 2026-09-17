package com.team3.gudit.auth.oauth2

import com.team3.gudit.user.domain.entity.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

class CustomOAuth2User(
    val user: User?,
    val provider: AuthProvider,
    val userInfo: OAuth2UserInfo,
    private val attributes: Map<String, Any>,
    private val principalName: String,
) : OAuth2User {
    fun isRegistered(): Boolean = user != null
    override fun getAttributes(): Map<String, Any> = attributes
    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority(user?.role?.name ?: "USER"))
    override fun getName(): String = principalName
}
