package com.team3.gudit.auth.service

import com.team3.gudit.auth.oauth2.AuthProvider
import com.team3.gudit.user.domain.entity.Role
import com.team3.gudit.user.domain.entity.User
import com.team3.gudit.user.domain.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.Optional

class CustomOAuth2UserServiceTest {
    private val repository = mock(UserRepository::class.java)
    private val service = CustomOAuth2UserService(repository, "")
    private val restTemplate = RestTemplate()
    private val server = MockRestServiceServer.bindTo(restTemplate).build()
    private val request = createRequest("id")

    init {
        service.setRestOperations(restTemplate)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "{\"id\":123}",
        "{\"id\":123,\"kakao_account\":{}}",
        "{\"id\":123,\"kakao_account\":{\"email\":null}}",
    ])
    fun `이메일이 누락되면 사용자 조회와 저장 없이 인증을 거부한다`(response: String) {
        server.expect(requestTo("https://example.test/userinfo"))
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON))

        val exception = assertThrows(OAuth2AuthenticationException::class.java) { service.loadUser(request) }

        assertEquals("email_required", exception.error.errorCode)
        verifyNoInteractions(repository)
        server.verify()
    }

    @Test
    fun `정상 이메일을 가진 기존 사용자는 로그인할 수 있다`() {
        val user = User(kakaoId = 123L, email = "user@example.com", nickname = "before", role = Role.USER, provider = AuthProvider.KAKAO)
        `when`(repository.findByKakaoIdAndProvider(123L, AuthProvider.KAKAO)).thenReturn(Optional.of(user))
        server.expect(requestTo("https://example.test/userinfo"))
            .andRespond(withSuccess(
                """{"id":123,"kakao_account":{"email":"user@example.com","profile":{"nickname":"after"}}}""",
                MediaType.APPLICATION_JSON,
            ))

        service.loadUser(request)

        assertEquals("after", user.nickname)
        assertEquals("user@example.com", user.email)
        verify(repository).findByKakaoIdAndProvider(123L, AuthProvider.KAKAO)
        verifyNoMoreInteractions(repository)
        server.verify()
    }

    private fun createRequest(nameAttributeKey: String): OAuth2UserRequest {
        val registration = ClientRegistration.withRegistrationId("kakao")
            .clientId("test-client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost/login/oauth2/code/kakao")
            .authorizationUri("https://example.test/authorize")
            .tokenUri("https://example.test/token")
            .userInfoUri("https://example.test/userinfo")
            .userNameAttributeName(nameAttributeKey)
            .build()
        return OAuth2UserRequest(
            registration,
            OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-token",
                Instant.now(),
                Instant.now().plusSeconds(60),
            ),
        )
    }
}
