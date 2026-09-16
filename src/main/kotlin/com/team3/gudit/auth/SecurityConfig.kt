package com.team3.gudit.auth

import com.team3.gudit.auth.filter.InternalApiKeyFilter
import com.team3.gudit.auth.filter.TokenAuthenticationFilter
import com.team3.gudit.auth.oauth2.CustomAuthorizationRequestResolver
import com.team3.gudit.auth.oauth2.OAuth2FailureHandler
import com.team3.gudit.auth.oauth2.OAuth2SuccessHandler
import com.team3.gudit.auth.security.CustomAuthenticationEntryPoint
import com.team3.gudit.auth.service.CustomOAuth2UserService
import com.team3.gudit.global.logging.TraceIdFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val oAuth2SuccessHandler: OAuth2SuccessHandler,
    private val oAuth2FailureHandler: OAuth2FailureHandler,
    private val tokenAuthenticationFilter: TokenAuthenticationFilter,
    private val internalApiKeyFilter: InternalApiKeyFilter,
    private val authenticationEntryPoint: CustomAuthenticationEntryPoint,
    private val customAuthorizationRequestResolver: CustomAuthorizationRequestResolver,
    private val traceIdFilter: TraceIdFilter,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity, customOAuth2UserService: CustomOAuth2UserService): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(
                    "/",
                    // 정적 리소스
                    "/css/**", "/js/**", "/images/**", "/swagger-ui", "/actuator/**",
                    // OAuth2
                    "/login-success.html", "/login-failure.html", "/oauth2/**", "/login/**", "/api/auth/reissue",
                    // Toss Webhook
                    "/api/webhooks/toss/**",
                    // 사용자 화면
                    "/sales", "/sales/**", "/payments", "/payments/**", "/mypage/**",
                    // 기존 결제 테스트 화면
                    "/payments/test", "/payments/test/**",
                ).permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/internal/performance-probe/server-error", "/api/internal/performance-probe/slow-response").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/internal/performance-probe/rollback-failure").permitAll()
                    // 내부 API는 InternalApiKeyFilter에서 별도로 인증한다.
                    .requestMatchers("/api/internal/**").permitAll()
                    // 판매 조회
                    .requestMatchers(HttpMethod.GET, "/api/sales", "/api/sales/**").permitAll()
                    // 관리자 화면 / 상품 관리
                    .requestMatchers("/admin/**", "/api/goods/**").hasAuthority("ADMIN")
                    // 판매 등록·수정·삭제
                    .requestMatchers(HttpMethod.POST, "/api/sales").hasAuthority("ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/api/sales/**").hasAuthority("ADMIN")
                    .requestMatchers(HttpMethod.DELETE, "/api/sales/**").hasAuthority("ADMIN")
                    .requestMatchers("/api/**").hasAnyAuthority("USER", "ADMIN")
                    .anyRequest().authenticated()
            }
            .oauth2Login { oauth ->
                oauth.authorizationEndpoint { it.authorizationRequestResolver(customAuthorizationRequestResolver) }
                    .userInfoEndpoint { it.userService(customOAuth2UserService) }
                    .successHandler(oAuth2SuccessHandler)
                    .failureHandler(oAuth2FailureHandler)
            }
            .exceptionHandling {
                it.defaultAuthenticationEntryPointFor(authenticationEntryPoint, PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
            }
            .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(traceIdFilter, TokenAuthenticationFilter::class.java)
        return http.build()
    }
}
