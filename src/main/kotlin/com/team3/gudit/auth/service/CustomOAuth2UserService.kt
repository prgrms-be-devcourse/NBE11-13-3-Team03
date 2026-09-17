package com.team3.gudit.auth.service

import com.team3.gudit.auth.oauth2.AuthProvider
import com.team3.gudit.auth.oauth2.CustomOAuth2User
import com.team3.gudit.auth.oauth2.OAuth2UserInfoFactory
import com.team3.gudit.user.domain.entity.Role
import com.team3.gudit.user.domain.entity.User
import com.team3.gudit.user.domain.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository,
    @Value("\${app.admin-kakao-ids:}") private val adminKakaoIds: String,
) : DefaultOAuth2UserService() {
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oauthUser = super.loadUser(userRequest)
        val registration = userRequest.clientRegistration
        val provider = AuthProvider.from(registration.registrationId)
        val info = OAuth2UserInfoFactory.of(provider, oauthUser.attributes)
        val kakaoId = info.id() ?: throw OAuth2AuthenticationException(
            OAuth2Error("kakao_id_required"),
            "SNS 계정에서 사용자 식별자를 가져오지 못했습니다.",
        )
        if (info.email() == null) {
            throw OAuth2AuthenticationException(
                OAuth2Error("email_required"),
                "SNS 계정에서 이메일을 가져오지 못했습니다. 이메일 제공 동의가 필요합니다.",
            )
        }
        val principalName = oauthUser.name
        val user = userRepository.findByKakaoIdAndProvider(kakaoId, provider)
            .map { existing ->
                existing.updateProfile(info.name())
                if (isAdmin(kakaoId) && existing.role != Role.ADMIN) existing.promoteToAdmin()
                existing
            }
            .orElseGet {
                userRepository.save(
                    User(
                        kakaoId = kakaoId,
                        nickname = info.name(),
                        email = info.email(),
                        role = if (isAdmin(kakaoId)) Role.ADMIN else Role.USER,
                        provider = provider,
                    ),
                )
            }
        return CustomOAuth2User(user, provider, info, oauthUser.attributes, principalName)
    }

    private fun isAdmin(kakaoId: Long?): Boolean {
        if (adminKakaoIds.isBlank()) return false
        val adminIds = adminKakaoIds.split(',').map { it.trim() }.filter { it.isNotBlank() }.map { it.toLong() }.toSet()
        return kakaoId in adminIds
    }
}
