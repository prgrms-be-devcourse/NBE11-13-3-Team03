package com.team3.gudit.auth.oauth2

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.stereotype.Component

@Component
class CustomAuthorizationRequestResolver(
    clientRegistrationRepository: ClientRegistrationRepository,
) : OAuth2AuthorizationRequestResolver {
    private val delegate = DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization")

    override fun resolve(request: HttpServletRequest): OAuth2AuthorizationRequest? = customize(delegate.resolve(request))
    override fun resolve(request: HttpServletRequest, clientRegistrationId: String): OAuth2AuthorizationRequest? =
        customize(delegate.resolve(request, clientRegistrationId))

    private fun customize(request: OAuth2AuthorizationRequest?): OAuth2AuthorizationRequest? {
        if (request == null) return null
        val parameters = HashMap(request.additionalParameters)
        parameters["prompt"] = "select_account"
        return OAuth2AuthorizationRequest.from(request).additionalParameters(parameters).build()
    }
}
